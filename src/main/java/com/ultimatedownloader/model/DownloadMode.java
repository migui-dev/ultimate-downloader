package com.ultimatedownloader.model;

import com.ultimatedownloader.util.Textos;

/** Los tres modos de descarga que ofrece la aplicación. */
public enum DownloadMode {

    VIDEO_Y_AUDIO("Vídeo + audio"),
    SOLO_VIDEO("Solo vídeo"),
    SOLO_AUDIO("Solo audio");

    private final String nombreLegible;

    DownloadMode(String nombreLegible) {
        this.nombreLegible = nombreLegible;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("modo", name(), nombreLegible);
    }

    public boolean incluyeVideo() {
        return this != SOLO_AUDIO;
    }

    public boolean incluyeAudio() {
        return this != SOLO_VIDEO;
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
