package com.ultimatedownloader.service.convert;

import com.ultimatedownloader.model.DownloadPhase;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.Tarea;
import com.ultimatedownloader.model.convert.ConversionJob;
import com.ultimatedownloader.model.convert.ConversionRequest;
import com.ultimatedownloader.service.ErrorTranslator;
import com.ultimatedownloader.service.FilenameTemplate;
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
 * Ejecuta una conversión con ffmpeg dentro de la cola. ffmpeg escribe el progreso por la
 * salida estándar y sus mensajes por la de error: el progreso mueve la barra y los mensajes
 * van a la consola.
 */
public final class EjecutorConversion implements EjecutorTarea {

    private static final Logger log = LoggerFactory.getLogger(EjecutorConversion.class);

    /** Margen que se exige libre además del tamaño estimado del resultado. */
    private static final long MARGEN_BYTES = 200L * 1024 * 1024;

    private final FfmpegArgumentBuilder argumentos;

    public EjecutorConversion(Path ffmpeg) {
        this.argumentos = new FfmpegArgumentBuilder(ffmpeg);
    }

    @Override
    public ResultadoTarea ejecutar(Tarea tarea, SeguimientoTarea seguimiento)
            throws InterruptedException {
        ConversionJob trabajo = (ConversionJob) tarea;
        ConversionRequest peticion = trabajo.peticion();

        if (!Files.isRegularFile(peticion.origen())) {
            return ResultadoTarea.fallo(new ErrorAmistoso(
                    Textos.t("separacion.archivo_no_esta", peticion.origen().getFileName()),
                    Textos.t("convertir.vuelve_a_anadirlo")));
        }

        long necesario = peticion.info() != null ? peticion.info().tamanoBytes() : 0;
        FolderValidator.Resultado carpeta =
                FolderValidator.validar(peticion.carpetaDestino(), necesario + MARGEN_BYTES);
        if (!carpeta.valida()) {
            return ResultadoTarea.fallo(new ErrorAmistoso(carpeta.mensaje(),
                    Textos.t("comun.cambia_la_carpeta")));
        }

        FfmpegProgressParser progreso = new FfmpegProgressParser(peticion.duracionSalida());
        List<String> mensajes = Collections.synchronizedList(new ArrayList<>());

        seguimiento.fase(peticion.remuxear() ? DownloadPhase.REMUXEANDO : DownloadPhase.CONVIRTIENDO);
        seguimiento.dispositivo(peticion.usarGpu() ? "GPU · NVENC" : "CPU");

        List<String> orden = argumentos.construir(peticion);
        log.debug("Conversión {}: {}", tarea.id(), orden);

        ProcessRunner.ProcesoEnCurso proceso;
        try {
            proceso = ProcessRunner.lanzar(orden,
                    linea -> progreso.procesar(linea).ifPresent(seguimiento::progreso),
                    linea -> {
                        mensajes.add(linea);
                        seguimiento.consola(linea);
                    });
        } catch (IOException e) {
            return ResultadoTarea.fallo(new ErrorAmistoso(
                    Textos.t("convertir.no_se_ha_podido_ejecutar"),
                    Textos.t("convertir.comprueba_su_ruta"), e.toString()));
        }
        seguimiento.registrarProceso(proceso);

        int codigo = proceso.esperar();

        if (proceso.cancelado() || seguimiento.cancelado()) {
            return ResultadoTarea.CANCELADA;
        }
        if (codigo == 0) {
            seguimiento.archivoFinal(peticion.destino());
            seguimiento.progreso(progreso.instantanea());
            return ResultadoTarea.CORRECTO;
        }

        // Solo se queda con el final de la salida: ffmpeg es muy hablador y el error está al final.
        String detalle = ultimasLineas(mensajes, 25);
        log.warn("Conversión {} fallida (código {}): {}", tarea.id(), codigo, detalle);
        return ResultadoTarea.fallo(ErrorTranslator.traducir(codigo, detalle));
    }

    @Override
    public void limpiar(Tarea tarea) {
        ConversionRequest peticion = ((ConversionJob) tarea).peticion();
        Path destino = peticion.destino();
        // Un archivo a medio escribir no sirve para nada y confunde: fuera.
        try {
            if (Files.deleteIfExists(destino)) {
                log.debug("Borrado el archivo incompleto {}", destino);
            }
        } catch (IOException e) {
            log.debug("No se ha podido borrar {}: {}", destino, e.getMessage());
        }
    }

    @Override
    public void prepararReintento(Tarea tarea) {
        ConversionJob trabajo = (ConversionJob) tarea;
        ConversionRequest original = trabajo.peticion();
        String nombre = FilenameTemplate.asegurarUnico(original.carpetaDestino(),
                original.nombreBase(), original.formato().extension());
        trabajo.peticionProperty().set(original.copia().nombreBase(nombre).build());
    }

    private static String ultimasLineas(List<String> lineas, int cuantas) {
        synchronized (lineas) {
            int desde = Math.max(0, lineas.size() - cuantas);
            return String.join("\n", lineas.subList(desde, lineas.size()));
        }
    }
}
