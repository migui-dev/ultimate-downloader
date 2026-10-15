package com.ultimatedownloader.model;

/** Tamaño total estimado. Aproximado si alguna pista no traía tamaño o hay conversión. */
public record EstimacionTamano(long bytes, boolean aproximado) {

    public static final EstimacionTamano DESCONOCIDA = new EstimacionTamano(0, true);

    public boolean conocida() {
        return bytes > 0;
    }
}
