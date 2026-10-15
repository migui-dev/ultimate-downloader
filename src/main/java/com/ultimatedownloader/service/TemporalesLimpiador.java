package com.ultimatedownloader.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Borra los restos de una descarga interrumpida: los nombre.fNNN.ext de cada pista y los .part
 * y .ytdl de la descarga en curso.
 *
 * El nombre base es único en la carpeta, se comprueba al encolar, así que todo lo que empiece
 * por él es de esa descarga y se puede borrar sin tocar nada del usuario.
 */
public final class TemporalesLimpiador {

    private static final Logger log = LoggerFactory.getLogger(TemporalesLimpiador.class);

    /** Extensiones temporales que deja yt-dlp. */
    private static final List<String> SUFIJOS = List.of(".part", ".ytdl", ".temp");

    /**
     * Miniaturas que yt-dlp baja para incrustarlas. Normalmente las borra él, pero si la descarga
     * se cancela se quedan sueltas. Ninguna de estas extensiones puede ser el archivo final.
     */
    private static final List<String> MINIATURAS = List.of(".webp", ".jpg", ".jpeg", ".png");

    private TemporalesLimpiador() {
    }

    /** Borra todos los temporales de una descarga. Devuelve cuántos ha borrado. */
    public static int limpiar(Path carpeta, String nombreBase) {
        if (nombreBase == null || nombreBase.isBlank() || carpeta == null
                || !Files.isDirectory(carpeta)) {
            return 0;
        }
        int borrados = 0;
        try (Stream<Path> archivos = Files.list(carpeta)) {
            for (Path ruta : archivos.filter(r -> esTemporalDe(r, nombreBase)).toList()) {
                try {
                    if (Files.deleteIfExists(ruta)) {
                        borrados++;
                        log.debug("Borrado el temporal {}", ruta);
                    }
                } catch (IOException e) {
                    log.debug("No se ha podido borrar {}: {}", ruta, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.debug("No se ha podido listar {}: {}", carpeta, e.getMessage());
        }
        return borrados;
    }

    /**
     * El archivo final terminado no cuenta como temporal: si la descarga se canceló justo al
     * acabar, mejor dejarlo que borrar algo que el usuario ya tiene.
     */
    static boolean esTemporalDe(Path ruta, String nombreBase) {
        String nombre = ruta.getFileName().toString();
        if (!nombre.startsWith(nombreBase)) {
            return false;
        }
        String enMinusculas = nombre.toLowerCase(java.util.Locale.ROOT);
        if (SUFIJOS.stream().anyMatch(enMinusculas::endsWith)
                || MINIATURAS.stream().anyMatch(enMinusculas::endsWith)) {
            return true;
        }
        String resto = nombre.substring(nombreBase.length());
        // Pistas sueltas que yt-dlp nombra «nombre.f401.mp4» antes de unirlas.
        return resto.matches("\\.f\\d+\\..+")
                || resto.matches("\\.f\\d+-Frag\\d+.*")
                || resto.matches("\\.part-Frag\\d+.*");
    }
}
