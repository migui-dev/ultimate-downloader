package com.ultimatedownloader.model;

/** Una pista de subtítulos disponible. El código es el que usa yt-dlp: es, en-US… */
public record SubtitleTrack(String codigo, String nombre, boolean automatico) {

    public String etiqueta() {
        return automatico ? nombre + " (automáticos)" : nombre;
    }

    @Override
    public String toString() {
        return etiqueta();
    }
}
