package com.ultimatedownloader.service.separacion;

import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.separacion.SeparacionRequest;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.ProcessRunner;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prepara con ffmpeg lo que se le va a dar al motor: sacar el audio de un vídeo y recortar la
 * muestra. Sin tocar la calidad, porque remuestrear antes de separar estropearía el resultado.
 *
 * Se descodifica siempre en vez de copiar la pista: un WAV no puede llevar dentro un AAC, y
 * pcm_s32le no pierde nada de ningún origen.
 */
public final class ExtractorAudio {

    private static final Logger log = LoggerFactory.getLogger(ExtractorAudio.class);

    private static final Duration LIMITE = Duration.ofMinutes(30);

    /** Extensiones que se tratan como vídeo y hay que pasar por aquí antes de separar. */
    private static final List<String> VIDEO = List.of(
            "mkv", "mp4", "mov", "webm", "avi", "m4v", "mpg", "mpeg", "wmv", "flv", "ts", "m2ts");

    private final Path ffmpeg;

    public ExtractorAudio(Path ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    /** Si el archivo es un vídeo, por su extensión. */
    public static boolean esVideo(Path archivo) {
        String nombre = archivo.getFileName().toString().toLowerCase(Locale.ROOT);
        int punto = nombre.lastIndexOf('.');
        return punto > 0 && VIDEO.contains(nombre.substring(punto + 1));
    }

    /**
     * Saca la pista de audio a un WAV temporal. No se indican frecuencia ni canales, así que
     * ffmpeg conserva los del original.
     */
    public void extraer(Path origen, Path destino, SeparacionRequest.Muestra muestra)
            throws AppException {
        List<String> orden = new ArrayList<>(List.of(
                ffmpeg.toString(), "-hide_banner", "-nostdin", "-y"));
        // Si solo se va a separar una muestra, se recorta ya aquí: así no hay que escribir el
        // audio entero de una película para quedarse con treinta segundos.
        if (muestra != null) {
            orden.add("-ss");
            orden.add(String.valueOf(muestra.desdeSegundo()));
        }
        orden.add("-i");
        orden.add(origen.toString());
        if (muestra != null) {
            orden.add("-t");
            orden.add(String.valueOf(muestra.segundos()));
        }
        orden.addAll(List.of(
                "-vn", "-sn", "-dn",
                "-map", "0:a:0",
                "-c:a", "pcm_s32le",
                destino.toString()));
        ejecutar(orden, Textos.t("extractor.no_se_ha_podido_sacar"));
    }

    /**
     * Deja una copia del vídeo con la pista separada. La imagen se copia sin recodificar, así que
     * tarda segundos. Sin -shortest: las dos pistas duran lo mismo, y con copia de vídeo esa
     * opción llena la cola del multiplexor y aborta.
     */
    public void reemplazarAudio(Path video, Path pista, Path destino) throws AppException {
        List<String> orden = new ArrayList<>(List.of(
                ffmpeg.toString(), "-hide_banner", "-nostdin", "-y",
                "-i", video.toString(),
                "-i", pista.toString(),
                "-map", "0:v",
                "-map", "1:a",
                "-c:v", "copy",
                "-c:a", "aac", "-b:a", "320k",
                destino.toString()));
        ejecutar(orden, Textos.t("extractor.no_se_ha_podido_montar"));
    }

    private void ejecutar(List<String> orden, String mensajeDeError) throws AppException {
        log.debug("ffmpeg: {}", orden);
        try {
            ProcessRunner.Resultado resultado = ProcessRunner.ejecutar(orden, LIMITE);
            if (!resultado.correcto()) {
                throw new AppException(new ErrorAmistoso(mensajeDeError,
                        Textos.t("extractor.mira_la_consola"),
                        ultimasLineas(resultado.error())));
            }
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(mensajeDeError,
                    Textos.t("extractor.comprueba_la_ruta"), e.toString()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(new ErrorAmistoso(Textos.t("error.cancelada"), null), e);
        }
    }

    /** ffmpeg es muy hablador y lo que importa está al final. */
    private static String ultimasLineas(String salida) {
        if (salida == null) {
            return null;
        }
        List<String> lineas = salida.lines().toList();
        int desde = Math.max(0, lineas.size() - 15);
        return String.join("\n", lineas.subList(desde, lineas.size()));
    }
}
