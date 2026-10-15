package com.ultimatedownloader.model;

import com.ultimatedownloader.util.Textos;

/**
 * Formatos de salida del modo «Solo audio». ORIGINAL no toca nada: se guarda tal cual lo
 * entrega YouTube; el resto pasan por ffmpeg.
 */
public enum AudioOutputFormat {

    ORIGINAL("Original (sin recomprimir)", null, null, null),
    WAV("WAV (sin pérdida, sin comprimir)", "wav", null, "wav"),
    FLAC("FLAC (sin pérdida)", "flac", null, "flac"),
    MP3_128("MP3 128 kbps", "mp3", "128K", "mp3"),
    MP3_192("MP3 192 kbps", "mp3", "192K", "mp3"),
    MP3_256("MP3 256 kbps", "mp3", "256K", "mp3"),
    MP3_320("MP3 320 kbps", "mp3", "320K", "mp3"),
    M4A("M4A (AAC)", "m4a", null, "m4a"),
    OPUS("Opus", "opus", null, "opus");

    private final String nombreLegible;
    private final String codigoYtDlp;
    private final String calidad;
    private final String extension;

    AudioOutputFormat(String nombreLegible, String codigoYtDlp, String calidad, String extension) {
        this.nombreLegible = nombreLegible;
        this.codigoYtDlp = codigoYtDlp;
        this.calidad = calidad;
        this.extension = extension;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("formato_audio", name(), nombreLegible);
    }

    /** Valor para --audio-format; null si no hay que extraer ni convertir. */
    public String codigoYtDlp() {
        return codigoYtDlp;
    }

    /** Valor para --audio-quality; null si no aplica. */
    public String calidad() {
        return calidad;
    }

    /** Extensión final del archivo, o null si depende del formato de origen. */
    public String extension() {
        return extension;
    }

    public boolean requiereConversion() {
        return codigoYtDlp != null;
    }

    /** Bitrate aproximado en kbps para estimar el tamaño; 0 si depende del origen. */
    public int bitrateAproximadoKbps() {
        return switch (this) {
            case ORIGINAL -> 0;
            case WAV -> 1536;
            case FLAC -> 900;
            case MP3_128 -> 128;
            case MP3_192 -> 192;
            case MP3_256 -> 256;
            case MP3_320 -> 320;
            case M4A -> 192;
            case OPUS -> 160;
        };
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
