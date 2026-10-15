package com.ultimatedownloader.model;

import com.ultimatedownloader.util.Textos;

/** Qué clase de trabajo es una tarea de la cola. */
public enum TipoTarea {

    DESCARGA("Descarga"),
    CONVERSION("Conversión"),
    SEPARACION("Separación");

    private final String nombreLegible;

    TipoTarea(String nombreLegible) {
        this.nombreLegible = nombreLegible;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("tipo_tarea", name(), nombreLegible);
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
