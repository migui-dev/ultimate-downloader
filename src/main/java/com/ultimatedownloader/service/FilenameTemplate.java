package com.ultimatedownloader.service;

import com.ultimatedownloader.model.AudioFormat;
import com.ultimatedownloader.model.VideoFormat;
import com.ultimatedownloader.model.VideoInfo;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resuelve la plantilla del nombre de archivo y la deja en un nombre válido en Windows.
 *
 * Los marcadores van entre llaves y no con la sintaxis de yt-dlp porque el nombre lo genera la
 * aplicación: así se puede comprobar antes que es legal y que no pisa nada.
 */
public final class FilenameTemplate {

    public static final String PLANTILLA_POR_DEFECTO = "{titulo}";

    /** Caracteres que Windows no admite en un nombre de archivo. */
    private static final String PROHIBIDOS = "<>:\"/\\|?*";

    /** Nombres reservados por el sistema: un archivo no puede llamarse así. */
    private static final Set<String> RESERVADOS = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    /** Longitud máxima del nombre, para no pasarse del límite de ruta de Windows. */
    private static final int LARGO_MAXIMO = 150;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private FilenameTemplate() {
    }

    /** Marcadores admitidos, con una descripción para mostrarla en los ajustes. */
    public static Map<String, String> marcadores() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("{titulo}", "Título del vídeo");
        m.put("{canal}", "Nombre del canal");
        m.put("{id}", "Identificador del vídeo");
        m.put("{fecha}", "Fecha de publicación (2026-10-13)");
        m.put("{calidad}", "Calidad del vídeo (1080p60)");
        m.put("{resolucion}", "Resolución (1920x1080)");
        m.put("{codec}", "Códec de vídeo (AV1, VP9, H264)");
        m.put("{audio}", "Códec de audio (Opus, AAC)");
        m.put("{fps}", "Fotogramas por segundo");
        return m;
    }

    /** Sustituye los marcadores y devuelve el nombre ya saneado, sin extensión. */
    public static String aplicar(String plantilla, VideoInfo info, VideoFormat video, AudioFormat audio) {
        String base = plantilla == null || plantilla.isBlank() ? PLANTILLA_POR_DEFECTO : plantilla;
        String resultado = base
                .replace("{titulo}", valor(info != null ? info.titulo() : null))
                .replace("{canal}", valor(info != null ? info.canal() : null))
                .replace("{id}", valor(info != null ? info.id() : null))
                .replace("{fecha}", info != null && info.fechaSubida() != null
                        ? info.fechaSubida().format(FECHA) : "")
                .replace("{calidad}", video != null ? video.etiquetaCalidad() : "")
                .replace("{resolucion}", video != null && video.ancho() > 0
                        ? video.ancho() + "x" + video.alto() : "")
                .replace("{codec}", video != null ? video.codec().nombreCorto() : "")
                .replace("{audio}", audio != null ? audio.codec().nombreCorto() : "")
                .replace("{fps}", video != null && video.fps() > 0
                        ? String.valueOf(Math.round(video.fps())) : "");
        return sanear(resultado);
    }

    /** Convierte cualquier texto en un nombre de archivo válido en Windows. */
    public static String sanear(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "video";
        }
        StringBuilder sb = new StringBuilder(nombre.length());
        for (int i = 0; i < nombre.length(); i++) {
            char c = nombre.charAt(i);
            if (c < 0x20 || c == 0x7F || PROHIBIDOS.indexOf(c) >= 0) {
                sb.append('-');
            } else {
                sb.append(c);
            }
        }
        String limpio = sb.toString()
                .replaceAll("-{2,}", "-")
                .replaceAll("\\s{2,}", " ")
                .strip();

        // Windows no admite nombres que terminen en punto o espacio.
        limpio = limpio.replaceAll("[ .]+$", "").strip();

        if (limpio.length() > LARGO_MAXIMO) {
            limpio = limpio.substring(0, LARGO_MAXIMO).strip().replaceAll("[ .]+$", "");
        }
        if (limpio.isBlank()) {
            return "video";
        }
        if (RESERVADOS.contains(limpio.toUpperCase(Locale.ROOT))) {
            return "_" + limpio;
        }
        return limpio;
    }

    /** Un nombre libre en la carpeta, añadiendo (2), (3)… si hace falta. */
    public static String asegurarUnico(Path carpeta, String base, String... extensiones) {
        if (carpeta == null || base == null || base.isBlank()) {
            return base;
        }
        String candidato = base;
        int intento = 2;
        while (existeAlguno(carpeta, candidato, extensiones) && intento < 1000) {
            candidato = base + " (" + intento + ")";
            intento++;
        }
        return candidato;
    }

    private static boolean existeAlguno(Path carpeta, String nombre, String... extensiones) {
        if (extensiones == null || extensiones.length == 0) {
            return resuelveYExiste(carpeta, nombre);
        }
        for (String ext : extensiones) {
            if (ext == null || ext.isBlank()) {
                continue;
            }
            if (resuelveYExiste(carpeta, nombre + "." + ext)) {
                return true;
            }
        }
        return false;
    }

    private static boolean resuelveYExiste(Path carpeta, String nombre) {
        try {
            return Files.exists(carpeta.resolve(nombre));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private static String valor(String texto) {
        return texto != null ? texto : "";
    }
}
