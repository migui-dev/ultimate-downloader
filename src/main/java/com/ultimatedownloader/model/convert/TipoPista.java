package com.ultimatedownloader.model.convert;

import com.ultimatedownloader.util.Textos;
import java.util.Locale;

/** Clase de pista dentro de un archivo multimedia. */
public enum TipoPista {

    VIDEO("Vídeo"),
    AUDIO("Audio"),
    SUBTITULO("Subtítulos"),
    OTRO("Otra");

    private final String nombreLegible;

    TipoPista(String nombreLegible) {
        this.nombreLegible = nombreLegible;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("tipo_pista", name(), nombreLegible);
    }

    /** Traduce el codec_type de ffprobe. */
    public static TipoPista desdeFfprobe(String tipo) {
        if (tipo == null) {
            return OTRO;
        }
        return switch (tipo.toLowerCase(Locale.ROOT)) {
            case "video" -> VIDEO;
            case "audio" -> AUDIO;
            case "subtitle" -> SUBTITULO;
            default -> OTRO;
        };
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
