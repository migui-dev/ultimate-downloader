package com.ultimatedownloader.model.calidad;

import com.ultimatedownloader.util.Textos;
import java.util.List;
import java.util.Locale;

/**
 * Formatos de salida que admite la aplicación. Cada uno sabe su extensión, si es contenedor
 * de audio o de vídeo y si puede guardar sin pérdida.
 */
public enum FormatoSalida {

    // ---- audio ----
    WAV("wav", "WAV", true, true),
    FLAC("flac", "FLAC", true, true),
    AIFF("aiff", "AIFF", true, true),
    M4A("m4a", "M4A (AAC)", true, false),
    MP3("mp3", "MP3", true, false),
    OPUS("opus", "Opus", true, false),
    OGG("ogg", "OGG (Vorbis)", true, false),

    // ---- vídeo ----
    MKV("mkv", "MKV (Matroska)", false, true),
    MP4("mp4", "MP4", false, false),
    MOV("mov", "MOV (QuickTime)", false, true),
    WEBM("webm", "WebM", false, false),
    AVI("avi", "AVI", false, true);

    private final String extension;
    private final String nombreLegible;
    private final boolean audio;
    private final boolean admiteSinPerdida;

    FormatoSalida(String extension, String nombreLegible, boolean audio, boolean admiteSinPerdida) {
        this.extension = extension;
        this.nombreLegible = nombreLegible;
        this.audio = audio;
        this.admiteSinPerdida = admiteSinPerdida;
    }

    public String extension() {
        return extension;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("formato_salida", name(), nombreLegible);
    }

    /** true si es un contenedor de audio; false si es de vídeo. */
    public boolean esAudio() {
        return audio;
    }

    public boolean esVideo() {
        return !audio;
    }

    /** true si en este formato se puede guardar sin pérdida de calidad. */
    public boolean admiteSinPerdida() {
        return admiteSinPerdida;
    }

    public static List<FormatoSalida> deAudio() {
        return List.of(WAV, FLAC, AIFF, M4A, MP3, OPUS, OGG);
    }

    public static List<FormatoSalida> deVideo() {
        return List.of(MKV, MP4, MOV, WEBM, AVI);
    }

    public static FormatoSalida desdeExtension(String ext) {
        if (ext != null) {
            String limpio = ext.toLowerCase(Locale.ROOT).replace(".", "");
            for (FormatoSalida f : values()) {
                if (f.extension.equals(limpio)) {
                    return f;
                }
            }
            if ("m4v".equals(limpio)) {
                return MP4;
            }
            if ("oga".equals(limpio)) {
                return OGG;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
