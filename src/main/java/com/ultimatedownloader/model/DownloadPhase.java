package com.ultimatedownloader.model;

import com.ultimatedownloader.util.Textos;

/** Fase concreta por la que pasa una tarea; se muestra en la cola. */
public enum DownloadPhase {

    EN_COLA("En cola"),
    PREPARANDO("Preparando"),
    DESCARGANDO("Descargando"),
    DESCARGANDO_VIDEO("Descargando vídeo"),
    DESCARGANDO_AUDIO("Descargando audio"),
    DESCARGANDO_SUBTITULOS("Descargando subtítulos"),
    UNIENDO("Uniendo pistas"),
    REMUXEANDO("Cambiando de contenedor"),
    CONVIRTIENDO("Convirtiendo"),
    EXTRAYENDO_AUDIO("Extrayendo audio"),
    PREPARANDO_MUESTRA("Recortando la muestra"),
    DESCARGANDO_MODELO("Descargando el modelo"),
    CARGANDO_MODELO("Cargando el modelo"),
    SEPARANDO("Separando"),
    GUARDANDO_PISTAS("Guardando las pistas"),
    INCRUSTANDO("Incrustando miniatura y metadatos"),
    FINALIZANDO("Finalizando"),
    TERMINADA("Terminada");

    private final String nombreLegible;

    DownloadPhase(String nombreLegible) {
        this.nombreLegible = nombreLegible;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("fase", name(), nombreLegible);
    }

    public boolean esDescarga() {
        return this == DESCARGANDO || this == DESCARGANDO_VIDEO
                || this == DESCARGANDO_AUDIO || this == DESCARGANDO_SUBTITULOS;
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
