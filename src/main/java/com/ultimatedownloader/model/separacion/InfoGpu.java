package com.ultimatedownloader.model.separacion;

/** Tarjeta gráfica detectada. El controlador va a null y la memoria a 0 si no se saben. */
public record InfoGpu(String nombre, String controlador, int memoriaMb) {

    /** Texto corto para la interfaz: NVIDIA GeForce RTX 5070 · 12 GB. */
    public String resumen() {
        if (memoriaMb <= 0) {
            return nombre;
        }
        return nombre + " · " + Math.round(memoriaMb / 1024.0) + " GB";
    }
}
