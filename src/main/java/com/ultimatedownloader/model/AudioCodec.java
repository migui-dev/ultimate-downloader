package com.ultimatedownloader.model;

import com.ultimatedownloader.util.Textos;

/** Códecs de audio que entrega YouTube, con un nombre legible para la interfaz. */
public enum AudioCodec {

    OPUS("Opus", 4),
    AAC("AAC", 3),
    VORBIS("Vorbis", 2),
    MP3("MP3", 1),
    FLAC("FLAC", 5),
    AC3("AC-3", 1),
    EAC3("E-AC-3", 1),
    PCM("PCM", 5),
    DESCONOCIDO("Desconocido", -1),
    NINGUNO("Sin audio", -2);

    private final String nombreLegible;
    private final int preferencia;

    AudioCodec(String nombreLegible, int preferencia) {
        this.nombreLegible = nombreLegible;
        this.preferencia = preferencia;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("audio_codec", name(), nombreLegible);
    }

    /** Nombre sin espacios ni guiones, apto para usarlo en un nombre de archivo. */
    public String nombreCorto() {
        return nombreLegible.replace("-", "").replace(" ", "");
    }

    /** Mayor es mejor; solo se usa para desempatar formatos del mismo bitrate. */
    public int preferencia() {
        return preferencia;
    }

    /** Traduce el valor acodec del JSON de yt-dlp (mp4a.40.2, opus…). */
    public static AudioCodec desdeCadena(String acodec) {
        if (acodec == null || acodec.isBlank()) {
            return DESCONOCIDO;
        }
        if ("none".equalsIgnoreCase(acodec)) {
            return NINGUNO;
        }
        String c = acodec.toLowerCase(java.util.Locale.ROOT);
        if (c.startsWith("opus")) {
            return OPUS;
        }
        if (c.startsWith("mp4a") || c.startsWith("aac")) {
            return AAC;
        }
        if (c.startsWith("vorbis")) {
            return VORBIS;
        }
        if (c.startsWith("mp3") || c.startsWith("mpeg")) {
            return MP3;
        }
        if (c.startsWith("flac")) {
            return FLAC;
        }
        if (c.startsWith("ec-3") || c.startsWith("eac3")) {
            return EAC3;
        }
        if (c.startsWith("ac-3") || c.startsWith("ac3")) {
            return AC3;
        }
        if (c.startsWith("pcm")) {
            return PCM;
        }
        return DESCONOCIDO;
    }
}
