package com.ultimatedownloader.service;

import com.ultimatedownloader.model.DownloadPhase;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

/**
 * Traduce las líneas de yt-dlp a eventos de progreso. Las plantillas de esta clase son las que
 * se le pasan en --progress-template, así que la salida es un formato fijo separado por «;».
 */
public final class ProgressParser {

    /** Progreso de descarga: PROG;estado;descargados;total;total_estimado;velocidad;eta;id;vcodec;acodec. */
    public static final String PLANTILLA_DESCARGA =
            "PROG;%(progress.status)s;%(progress.downloaded_bytes)s;%(progress.total_bytes)s;"
            + "%(progress.total_bytes_estimate)s;%(progress.speed)s;%(progress.eta)s;"
            + "%(info.format_id)s;%(info.vcodec)s;%(info.acodec)s";

    /** Progreso de posproceso: PP;estado;nombreDelPosprocesador. */
    public static final String PLANTILLA_POSPROCESO =
            "postprocess:PP;%(progress.status)s;%(progress.postprocessor)s";

    /** Ruta definitiva del archivo, una vez movido a su sitio: RUTA;C:\.... */
    public static final String PLANTILLA_RUTA_FINAL = "after_move:RUTA;%(filepath)s";

    private ProgressParser() {
    }

    /** Evento deducido de una línea de yt-dlp. */
    public sealed interface Evento {
    }

    /** Avance de la descarga de una pista. Lo que yt-dlp no sepa viene a 0 o a null. */
    public record Descarga(DownloadPhase fase, String formatId, boolean terminado,
                           long descargados, long totales, double velocidad, Long eta)
            implements Evento {
    }

    /** Inicio o fin de un posproceso de ffmpeg. */
    public record Posproceso(DownloadPhase fase, boolean iniciado, String nombre) implements Evento {
    }

    /** Ruta definitiva del archivo generado. */
    public record RutaFinal(Path ruta) implements Evento {
    }

    /** Archivo de destino de una pista concreta (sirve para limpiar temporales). */
    public record Destino(Path ruta) implements Evento {
    }

    /** Mensaje de error de yt-dlp. */
    public record Fallo(String mensaje) implements Evento {
    }

    /**
     * Analiza una línea de la salida de yt-dlp.
     *
     * @return el evento correspondiente, o vacío si la línea no aporta nada
     */
    public static Optional<Evento> analizar(String linea) {
        if (linea == null) {
            return Optional.empty();
        }
        String l = linea.strip();
        if (l.isEmpty()) {
            return Optional.empty();
        }
        if (l.startsWith("PROG;")) {
            return analizarDescarga(l);
        }
        if (l.startsWith("PP;")) {
            return analizarPosproceso(l);
        }
        if (l.startsWith("RUTA;")) {
            return aRuta(l.substring(5)).map(RutaFinal::new);
        }
        if (l.startsWith("ERROR:")) {
            return Optional.of(new Fallo(l.substring(6).strip()));
        }
        if (l.startsWith("[download] Destination:")) {
            return aRuta(l.substring("[download] Destination:".length())).map(Destino::new);
        }
        int cierre = l.indexOf("] Destination:");
        if (l.startsWith("[") && cierre > 0) {
            return aRuta(l.substring(cierre + "] Destination:".length())).map(Destino::new);
        }
        return Optional.empty();
    }

    private static Optional<Evento> analizarDescarga(String linea) {
        String[] p = linea.split(";", -1);
        if (p.length < 7) {
            return Optional.empty();
        }
        String estado = p[1];
        long descargados = aLargo(p[2], 0);
        long totales = aLargo(p[3], 0);
        if (totales <= 0) {
            totales = aLargo(p[4], 0);
        }
        double velocidad = aDecimal(p[5], 0);
        Long eta = aLargoNulable(p[6]);
        String formatId = p.length > 7 ? aTexto(p[7]) : null;
        String vcodec = p.length > 8 ? aTexto(p[8]) : null;
        String acodec = p.length > 9 ? aTexto(p[9]) : null;

        boolean terminado = "finished".equalsIgnoreCase(estado);
        if ("error".equalsIgnoreCase(estado)) {
            return Optional.of(new Fallo("Fallo al descargar el formato " + formatId));
        }
        return Optional.of(new Descarga(faseDeCodecs(vcodec, acodec), formatId, terminado,
                descargados, totales, velocidad, eta));
    }

    private static Optional<Evento> analizarPosproceso(String linea) {
        String[] p = linea.split(";", -1);
        if (p.length < 3) {
            return Optional.empty();
        }
        boolean iniciado = !"finished".equalsIgnoreCase(p[1]);
        String nombre = aTexto(p[2]);
        return Optional.of(new Posproceso(faseDePosproceso(nombre), iniciado, nombre));
    }

    /** Si el formato trae vídeo se está descargando la pista de vídeo; si no, la de audio. */
    static DownloadPhase faseDeCodecs(String vcodec, String acodec) {
        boolean hayVideo = vcodec != null && !"none".equalsIgnoreCase(vcodec);
        boolean hayAudio = acodec != null && !"none".equalsIgnoreCase(acodec);
        if (hayVideo) {
            return DownloadPhase.DESCARGANDO_VIDEO;
        }
        if (hayAudio) {
            return DownloadPhase.DESCARGANDO_AUDIO;
        }
        return DownloadPhase.DESCARGANDO;
    }

    /** Traduce el nombre del posprocesador de yt-dlp a una fase en español. */
    static DownloadPhase faseDePosproceso(String nombre) {
        if (nombre == null) {
            return DownloadPhase.FINALIZANDO;
        }
        String n = nombre.toLowerCase(Locale.ROOT);
        if (n.contains("merger")) {
            return DownloadPhase.UNIENDO;
        }
        if (n.contains("remux")) {
            return DownloadPhase.REMUXEANDO;
        }
        if (n.contains("convertor") || n.contains("converter")) {
            return DownloadPhase.CONVIRTIENDO;
        }
        if (n.contains("extractaudio")) {
            return DownloadPhase.EXTRAYENDO_AUDIO;
        }
        if (n.contains("thumbnail") || n.contains("metadata") || n.contains("subtitle")) {
            return DownloadPhase.INCRUSTANDO;
        }
        return DownloadPhase.FINALIZANDO;
    }

    private static Optional<Path> aRuta(String valor) {
        String limpio = valor == null ? "" : valor.strip().replaceAll("^\"|\"$", "");
        if (limpio.isEmpty() || "NA".equals(limpio)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Paths.get(limpio));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /** yt-dlp escribe NA cuando un campo de la plantilla no tiene valor. */
    private static String aTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String v = valor.strip();
        return v.isEmpty() || "NA".equals(v) || "None".equals(v) ? null : v;
    }

    private static long aLargo(String valor, long porDefecto) {
        Long v = aLargoNulable(valor);
        return v != null ? v : porDefecto;
    }

    private static Long aLargoNulable(String valor) {
        String v = aTexto(valor);
        if (v == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static double aDecimal(String valor, double porDefecto) {
        String v = aTexto(valor);
        if (v == null) {
            return porDefecto;
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
