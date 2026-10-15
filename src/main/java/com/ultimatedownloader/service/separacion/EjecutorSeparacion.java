package com.ultimatedownloader.service.separacion;

import com.ultimatedownloader.model.DownloadPhase;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.ProgressSnapshot;
import com.ultimatedownloader.model.Tarea;
import com.ultimatedownloader.model.separacion.SeparacionJob;
import com.ultimatedownloader.model.separacion.SeparacionRequest;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.AppPaths;
import com.ultimatedownloader.service.FolderValidator;
import com.ultimatedownloader.service.ProcessRunner;
import com.ultimatedownloader.service.cola.EjecutorTarea;
import com.ultimatedownloader.service.cola.ResultadoTarea;
import com.ultimatedownloader.service.cola.SeguimientoTarea;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ejecuta una separación dentro de la cola. Antes prepara la entrada con ffmpeg si hace falta
 * —sacar el audio de un vídeo, recortar la muestra— y al terminar deja la copia del vídeo si
 * se ha pedido. Lo temporal se borra siempre, acabe bien, falle o se cancele.
 *
 * Con varios modelos «por separado» se lanza el motor una vez por modelo, en serie: dos a la
 * vez no caben en la memoria de la tarjeta.
 */
public final class EjecutorSeparacion implements EjecutorTarea {

    private static final Logger log = LoggerFactory.getLogger(EjecutorSeparacion.class);

    /** Margen de disco que se exige libre, además de lo que vayan a ocupar las pistas. */
    private static final long MARGEN_BYTES = 500L * 1024 * 1024;

    private final EntornoPython entorno;
    private final SeparacionArgumentBuilder argumentos;
    private final ExtractorAudio extractor;

    public EjecutorSeparacion(EntornoPython entorno, Path ffmpeg) {
        this.entorno = entorno;
        this.argumentos = new SeparacionArgumentBuilder(
                entorno.audioSeparator(), entorno.carpetaModelos());
        this.extractor = ffmpeg != null ? new ExtractorAudio(ffmpeg) : null;
    }

    @Override
    public ResultadoTarea ejecutar(Tarea tarea, SeguimientoTarea seguimiento)
            throws InterruptedException {
        SeparacionJob trabajo = (SeparacionJob) tarea;
        SeparacionRequest peticion = trabajo.peticion();

        ErrorAmistoso problema = comprobaciones(peticion);
        if (problema != null) {
            return ResultadoTarea.fallo(problema);
        }

        Path entrada;
        try {
            entrada = prepararEntrada(trabajo, seguimiento);
        } catch (AppException e) {
            return ResultadoTarea.fallo(e.error());
        }
        if (seguimiento.cancelado()) {
            return ResultadoTarea.CANCELADA;
        }

        List<List<String>> ordenes = ordenes(peticion, entrada);
        SeparacionProgressParser progreso = null;

        for (int i = 0; i < ordenes.size(); i++) {
            progreso = new SeparacionProgressParser();
            ResultadoTarea resultado = lanzar(trabajo, seguimiento, ordenes.get(i), progreso,
                    i, ordenes.size());
            if (resultado != ResultadoTarea.CORRECTO) {
                return resultado;
            }
            progreso.salidas().forEach(nombre ->
                    trabajo.anadirResultado(peticion.carpetaDestino().resolve(nombre)));
        }

        try {
            montarVideo(trabajo, seguimiento);
        } catch (AppException e) {
            return ResultadoTarea.fallo(e.error());
        }

        List<Path> resultados = trabajo.resultados();
        if (!resultados.isEmpty()) {
            seguimiento.archivoFinal(resultados.get(0));
        }
        seguimiento.progreso(new ProgressSnapshot(DownloadPhase.TERMINADA, 1, 0, 0, 0, null));
        return ResultadoTarea.CORRECTO;
    }

    @Override
    public void limpiar(Tarea tarea) {
        SeparacionJob trabajo = (SeparacionJob) tarea;
        for (Path temporal : trabajo.temporales()) {
            try {
                if (Files.deleteIfExists(temporal)) {
                    log.debug("Borrado el temporal {}", temporal);
                }
            } catch (IOException e) {
                log.debug("No se ha podido borrar {}: {}", temporal, e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ pasos

    private ErrorAmistoso comprobaciones(SeparacionRequest peticion) {
        if (!entorno.instalado()) {
            return new ErrorAmistoso(Textos.t("separacion.motor_no_instalado"),
                    Textos.t("separacion.instalalo"));
        }
        if (!Files.isRegularFile(peticion.entrada())) {
            return new ErrorAmistoso(
                    Textos.t("separacion.archivo_no_esta", peticion.entrada().getFileName()),
                    Textos.t("separacion.vuelve_a_elegirlo"));
        }
        long tamano = 0;
        try {
            tamano = Files.size(peticion.entrada());
        } catch (IOException e) {
            log.debug("No se ha podido medir {}: {}", peticion.entrada(), e.getMessage());
        }
        // Cada pista pesa como el original, y pueden salir varias por modelo.
        long necesario = tamano * 4L * peticion.modelos().size() + MARGEN_BYTES;
        FolderValidator.Resultado carpeta =
                FolderValidator.validar(peticion.carpetaDestino(), necesario);
        if (!carpeta.valida()) {
            return new ErrorAmistoso(carpeta.mensaje(),
                    Textos.t("comun.cambia_la_carpeta"));
        }
        return null;
    }

    /** Saca el audio del vídeo o recorta la muestra, si hace falta alguna de las dos cosas. */
    private Path prepararEntrada(SeparacionJob trabajo, SeguimientoTarea seguimiento)
            throws AppException {
        SeparacionRequest peticion = trabajo.peticion();
        Path original = peticion.entrada();
        boolean video = ExtractorAudio.esVideo(original);
        if (!video && peticion.muestra() == null) {
            return original;
        }
        if (extractor == null) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("separacion.hace_falta_ffmpeg"),
                    Textos.t("separacion.indica_su_ruta")));
        }

        Path temporal = temporal(trabajo);
        try {
            Files.createDirectories(temporal.getParent());
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("separacion.no_se_ha_podido_crear_temporal"),
                    Textos.t("separacion.comprueba_sitio_sistema"), e.toString()), e);
        }
        trabajo.anadirTemporal(temporal);
        trabajo.anadirTemporal(temporal.getParent());
        seguimiento.fase(video ? DownloadPhase.EXTRAYENDO_AUDIO
                : DownloadPhase.PREPARANDO_MUESTRA);
        extractor.extraer(original, temporal, peticion.muestra());
        return temporal;
    }

    private List<List<String>> ordenes(SeparacionRequest peticion, Path entrada) {
        if (peticion.esMezcla() || peticion.modelos().size() == 1) {
            return List.of(argumentos.construir(peticion, entrada));
        }
        // Modo «por separado»: una pasada por modelo, con la misma configuración.
        List<List<String>> ordenes = new ArrayList<>();
        for (String modelo : peticion.modelos()) {
            SeparacionRequest individual = new SeparacionRequest(
                    peticion.entrada(), peticion.carpetaDestino(), List.of(modelo),
                    SeparacionRequest.ModoVariosModelos.POR_SEPARADO, null, List.of(),
                    peticion.pistaUnica(), peticion.formato(), peticion.nivel(),
                    peticion.frecuenciaHz(), peticion.opciones(), peticion.muestra(),
                    peticion.reemplazarEnVideo());
            ordenes.add(argumentos.construir(individual, entrada));
        }
        return ordenes;
    }

    private ResultadoTarea lanzar(SeparacionJob trabajo, SeguimientoTarea seguimiento,
                                  List<String> orden, SeparacionProgressParser progreso,
                                  int indice, int total) throws InterruptedException {
        log.debug("Separación {} ({}/{}): {}", trabajo.id(), indice + 1, total, orden);
        List<String> mensajes = Collections.synchronizedList(new ArrayList<>());

        ProcessRunner.ProcesoEnCurso proceso;
        try {
            proceso = ProcessRunner.lanzar(orden, entorno.variables(),
                    linea -> {
                        mensajes.add(linea);
                        seguimiento.consola(linea);
                    },
                    linea -> {
                        mensajes.add(linea);
                        seguimiento.consola(linea);
                        progreso.procesar(linea).ifPresent(instantanea ->
                                informar(seguimiento, progreso, instantanea, indice, total));
                    });
        } catch (IOException e) {
            return ResultadoTarea.fallo(new ErrorAmistoso(
                    Textos.t("separacion.no_se_ha_podido_arrancar"),
                    Textos.t("separacion.vuelve_a_instalarlo"), e.toString()));
        }
        seguimiento.registrarProceso(proceso);

        int codigo = proceso.esperar();
        if (proceso.cancelado() || seguimiento.cancelado()) {
            return ResultadoTarea.CANCELADA;
        }
        if (codigo == 0) {
            return ResultadoTarea.CORRECTO;
        }
        String detalle = ultimasLineas(mensajes);
        log.warn("Separación {} fallida (código {}): {}", trabajo.id(), codigo, detalle);
        return ResultadoTarea.fallo(traducir(codigo, detalle));
    }

    /**
     * Lleva el progreso de una pasada al de la tarea entera: con tres modelos, el 50 % del segundo
     * es el 50 % de la tarea, para que la barra no vuelva atrás.
     */
    private static void informar(SeguimientoTarea seguimiento, SeparacionProgressParser progreso,
                                 ProgressSnapshot instantanea, int indice, int total) {
        if (progreso.dispositivo() != null) {
            seguimiento.dispositivo(progreso.dispositivo());
        }
        double fraccion = instantanea.fraccion();
        if (fraccion >= 0 && total > 1) {
            fraccion = (indice + fraccion) / total;
        }
        seguimiento.progreso(new ProgressSnapshot(instantanea.fase(), fraccion,
                instantanea.bytesDescargados(), instantanea.bytesTotales(),
                instantanea.velocidadBytesPorSeg(), instantanea.segundosRestantes()));
    }

    /** Si se ha pedido, deja una copia del vídeo con la primera pista separada. */
    private void montarVideo(SeparacionJob trabajo, SeguimientoTarea seguimiento)
            throws AppException {
        SeparacionRequest peticion = trabajo.peticion();
        if (!peticion.reemplazarEnVideo() || !ExtractorAudio.esVideo(peticion.entrada())
                || trabajo.resultados().isEmpty() || extractor == null) {
            return;
        }
        if (peticion.muestra() != null) {
            // La pista separada dura lo que la muestra y el vídeo dura lo que dure: montarlos
            // juntos daría un vídeo mudo a partir del segundo veinte.
            seguimiento.consola(Textos.t("separacion.no_se_monta_el_video"));
            return;
        }
        seguimiento.fase(DownloadPhase.FINALIZANDO);
        Path pista = trabajo.resultados().get(0);
        String base = sinExtension(peticion.entrada().getFileName().toString());
        Path destino = peticion.carpetaDestino().resolve(base + " (" + Textos.t("separacion.pista_separada") + ").mkv");
        extractor.reemplazarAudio(peticion.entrada(), pista, destino);
        trabajo.anadirResultado(destino);
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * WAV temporal con el que se alimenta al motor. Va en su propia subcarpeta y conserva el
     * nombre del original: el motor bautiza las pistas de salida con el nombre que recibe.
     */
    private static Path temporal(SeparacionJob trabajo) {
        String base = sinExtension(trabajo.peticion().entrada().getFileName().toString());
        return AppPaths.carpetaTemporal()
                .resolve("separacion-" + trabajo.id())
                .resolve(base + ".wav");
    }

    private static String sinExtension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }

    /** Traduce al español los fallos que se repiten; el resto se enseña tal cual. */
    static ErrorAmistoso traducir(int codigo, String detalle) {
        String texto = detalle == null ? "" : detalle;
        if (texto.contains("CUDA out of memory") || texto.contains("cuda runtime error")) {
            return new ErrorAmistoso(
                    Textos.t("separacion.sin_memoria_gpu"),
                    Textos.t("separacion.baja_el_segmento"), texto);
        }
        if (texto.contains("No such file or directory") || texto.contains("FileNotFoundError")) {
            return new ErrorAmistoso(
                    Textos.t("separacion.archivo_no_encontrado"),
                    Textos.t("separacion.comprueba_entrada"), texto);
        }
        if (texto.contains("ConnectionError") || texto.contains("Max retries exceeded")) {
            return new ErrorAmistoso(
                    Textos.t("separacion.no_se_ha_podido_descargar_modelo"),
                    Textos.t("entorno.comprueba_la_conexion"), texto);
        }
        if (texto.contains("ModuleNotFoundError")) {
            return new ErrorAmistoso(
                    Textos.t("separacion.falta_algo_por_instalar"),
                    Textos.t("separacion.borralo_y_reinstala"), texto);
        }
        return new ErrorAmistoso(
                Textos.t("separacion.ha_fallado"),
                Textos.t("separacion.detalle_en_la_consola"),
                Textos.t("separacion.codigo_del_motor", codigo)
                        + (texto.isBlank() ? "" : "\n" + texto));
    }

    private static String ultimasLineas(List<String> lineas) {
        synchronized (lineas) {
            int desde = Math.max(0, lineas.size() - 25);
            return String.join("\n", lineas.subList(desde, lineas.size()));
        }
    }
}
