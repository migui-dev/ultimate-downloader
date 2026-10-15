package com.ultimatedownloader.model;

import com.ultimatedownloader.util.Textos;

/** Estado de una descarga dentro de la cola. */
public enum JobStatus {

    EN_COLA("En cola"),
    EN_CURSO("En curso"),
    COMPLETADA("Completada"),
    CANCELADA("Cancelada"),
    ERROR("Error");

    private final String nombreLegible;

    JobStatus(String nombreLegible) {
        this.nombreLegible = nombreLegible;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("estado", name(), nombreLegible);
    }

    public boolean terminada() {
        return this == COMPLETADA || this == CANCELADA || this == ERROR;
    }

    public boolean sePuedeCancelar() {
        return this == EN_COLA || this == EN_CURSO;
    }

    public boolean sePuedeReintentar() {
        return this == CANCELADA || this == ERROR;
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
