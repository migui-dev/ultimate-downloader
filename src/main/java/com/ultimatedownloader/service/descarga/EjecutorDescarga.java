package com.ultimatedownloader.service.descarga;

import com.ultimatedownloader.model.DownloadJob;
import com.ultimatedownloader.model.DownloadRequest;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.Tarea;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.ArgumentBuilder;
import com.ultimatedownloader.service.ErrorTranslator;
import com.ultimatedownloader.service.FilenameTemplate;
import com.ultimatedownloader.service.FolderValidator;
import com.ultimatedownloader.service.ProcessRunner;
import com.ultimatedownloader.service.ProgressAggregator;
import com.ultimatedownloader.service.ProgressParser;
import com.ultimatedownloader.service.TemporalesLimpiador;
import com.ultimatedownloader.service.YtDlpService;
import com.ultimatedownloader.service.cola.EjecutorTarea;
import com.ultimatedownloader.service.cola.ResultadoTarea;
import com.ultimatedownloader.service.cola.SeguimientoTarea;
import com.ultimatedownloader.util.Textos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ejecuta una descarga de yt-dlp: lanza el proceso, traduce sus líneas a progreso y fases,
 * manda a la consola lo que no es progreso y limpia los temporales si algo sale mal.
 */
public final class EjecutorDescarga implements EjecutorTarea {

    private static final Logger log = LoggerFactory.getLogger(EjecutorDescarga.class);

    private final YtDlpService ytDlp;

    public EjecutorDescarga(YtDlpService ytDlp) {
        this.ytDlp = ytDlp;
    }

    @Override
    public ResultadoTarea ejecutar(Tarea tarea, SeguimientoTarea seguimiento)
            throws InterruptedException {
        DownloadJob descarga = (DownloadJob) tarea;
        DownloadRequest peticion = descarga.peticion();

        FolderValidator.Resultado carpeta =
                FolderValidator.validar(peticion.carpetaDestino(), peticion.estimacion().bytes());
        if (!carpeta.valida()) {
            return ResultadoTarea.fallo(new ErrorAmistoso(carpeta.mensaje(),
                    Textos.t("comun.cambia_la_carpeta")));
        }

        ProgressAggregator agregador = new ProgressAggregator(
                peticion.estimacion().bytes(), peticion.pistasPrevistas());
        List<String> errores = Collections.synchronizedList(new ArrayList<>());

        ProcessRunner.ProcesoEnCurso proceso;
        try {
            // yt-dlp escribe el progreso de la descarga por la salida estándar y el de los
            // posprocesos por la de error. De la de error solo se guarda lo que no es progreso.
            proceso = ytDlp.descargar(peticion,
                    linea -> {
                        procesarLinea(agregador, seguimiento, linea);
                        aConsola(seguimiento, linea);
                    },
                    linea -> {
                        procesarLinea(agregador, seguimiento, linea);
                        if (!esLineaDeProgreso(linea)) {
                            errores.add(linea);
                            aConsola(seguimiento, linea);
                        }
                        log.debug("[{}] {}", tarea.id(), linea);
                    });
        } catch (AppException e) {
            return ResultadoTarea.fallo(e.error());
        }
        seguimiento.registrarProceso(proceso);

        int codigo = proceso.esperar();

        if (proceso.cancelado() || seguimiento.cancelado()) {
            return ResultadoTarea.CANCELADA;
        }
        if (codigo == 0) {
            seguimiento.progreso(agregador.terminada());
            return ResultadoTarea.CORRECTO;
        }

        String detalle = String.join("\n", errores);
        log.warn("Descarga {} fallida (código {}): {}", tarea.id(), codigo, detalle);
        ErrorAmistoso error = ErrorTranslator.traducir(codigo, detalle);

        // Si el fallo es de los que se arreglan con otro cliente de YouTube, se apunta cuál
        // toca probar y se deja que la cola lo reintente.
        if (YtDlpService.esFalloDeCliente(detalle) && quedaOtroCliente(peticion)) {
            descarga.peticionProperty().set(peticion.copia()
                    .clienteAlternativo(siguienteCliente(peticion)).build());
            return ResultadoTarea.falloPasajero(error);
        }
        return esErrorPasajero(detalle)
                ? ResultadoTarea.falloPasajero(error)
                : ResultadoTarea.fallo(error);
    }

    @Override
    public void limpiar(Tarea tarea) {
        DownloadRequest peticion = ((DownloadJob) tarea).peticion();
        TemporalesLimpiador.limpiar(peticion.carpetaDestino(), peticion.nombreBase());
    }

    @Override
    public void prepararReintento(Tarea tarea) {
        DownloadJob descarga = (DownloadJob) tarea;
        DownloadRequest original = descarga.peticion();
        if (original.nombreBase() == null) {
            return;
        }
        // El nombre se recalcula: puede que ahora exista un archivo con ese nombre.
        String nombre = FilenameTemplate.asegurarUnico(original.carpetaDestino(),
                original.nombreBase(), original.extensionEsperada());
        descarga.peticionProperty().set(original.copia().nombreBase(nombre).build());
    }

    // ------------------------------------------------------------------ interno

    /** Traduce una línea de yt-dlp y refleja el cambio en la tarea. */
    private void procesarLinea(ProgressAggregator agregador, SeguimientoTarea seguimiento,
                               String linea) {
        ProgressParser.analizar(linea).ifPresent(evento -> {
            switch (evento) {
                case ProgressParser.Descarga descarga ->
                        seguimiento.progreso(agregador.aplicar(descarga));
                case ProgressParser.Posproceso posproceso ->
                        seguimiento.progreso(agregador.aplicar(posproceso));
                case ProgressParser.RutaFinal ruta -> seguimiento.archivoFinal(ruta.ruta());
                case ProgressParser.Destino destino -> log.debug("destino: {}", destino.ruta());
                case ProgressParser.Fallo fallo -> log.warn("{}", fallo.mensaje());
                default -> {
                    // El resto de líneas solo van a la consola.
                }
            }
        });
    }

    private void aConsola(SeguimientoTarea seguimiento, String linea) {
        if (!esLineaDeProgreso(linea)) {
            seguimiento.consola(linea);
        }
    }

    /** Las líneas de las plantillas de progreso no son errores aunque vengan por stderr. */
    public static boolean esLineaDeProgreso(String linea) {
        if (linea == null) {
            return false;
        }
        String l = linea.strip();
        return l.startsWith("PROG;") || l.startsWith("PP;") || l.startsWith("RUTA;");
    }

    /** true si todavía queda algún cliente de YouTube por probar. */
    private static boolean quedaOtroCliente(DownloadRequest peticion) {
        return siguienteCliente(peticion) != null;
    }

    /** El siguiente de la lista, o null si ya se han probado todos. */
    private static String siguienteCliente(DownloadRequest peticion) {
        List<String> clientes = ArgumentBuilder.CLIENTES_ALTERNATIVOS;
        String actual = peticion.clienteAlternativo();
        if (actual == null) {
            return clientes.get(0);
        }
        int siguiente = clientes.indexOf(actual) + 1;
        return siguiente > 0 && siguiente < clientes.size() ? clientes.get(siguiente) : null;
    }

    /**
     * Si merece la pena repetir la descarga tal cual. Solo los fallos de red o un rechazo
     * momentáneo: un vídeo privado va a fallar igual por mucho que se repita.
     */
    public static boolean esErrorPasajero(String salidaDeError) {
        if (salidaDeError == null || salidaDeError.isBlank()) {
            return false;
        }
        String texto = salidaDeError.toLowerCase(Locale.ROOT);
        return texto.contains("http error 403")
                || texto.contains("forbidden")
                || texto.contains("unable to download video data")
                || texto.contains("connection reset")
                || texto.contains("connection aborted")
                || texto.contains("read timed out")
                || texto.contains("timed out")
                || texto.contains("temporary failure");
    }
}
