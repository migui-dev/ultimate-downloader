package com.ultimatedownloader.model.calidad;

import com.ultimatedownloader.util.Textos;

/** Los tres niveles de calidad. Sus parámetros exactos están en Calidades. */
public enum NivelCalidad {

    /** Lo mejor posible técnicamente, sin mirar cuánto ocupa. */
    MAXIMA("Máxima", "Lo mejor posible, sin mirar el tamaño"),

    /** Equilibrio entre calidad y tamaño. */
    MEDIA("Media", "Buen equilibrio entre calidad y tamaño"),

    /** Lo más ligero que sigue siendo aceptable. */
    MINIMA("Mínima", "Lo más ligero, para compartir rápido");

    private final String nombreLegible;
    private final String descripcion;

    NivelCalidad(String nombreLegible, String descripcion) {
        this.nombreLegible = nombreLegible;
        this.descripcion = descripcion;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("nivel", name(), nombreLegible);
    }

    public String descripcion() {
        return Textos.deEnum("nivel_desc", name(), descripcion);
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
