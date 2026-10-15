package com.ultimatedownloader.model.separacion;

import java.nio.file.Path;

/**
 * Qué hay instalado ahora mismo en el entorno de Python. El acelerador y el dispositivo son
 * los comprobados contra PyTorch, y el aviso explica si ha caído a CPU sin quererlo.
 */
public record EstadoEntorno(boolean instalado, Path ejecutable, String version,
                            Acelerador acelerador, String dispositivo, String aviso) {

    /** Entorno que todavía no existe. */
    public static EstadoEntorno sinInstalar() {
        return new EstadoEntorno(false, null, null, Acelerador.CPU, null, null);
    }

    /** Texto corto para la interfaz: CUDA · NVIDIA GeForce RTX 5070. */
    public String resumenDispositivo() {
        if (dispositivo == null || dispositivo.isBlank()) {
            return acelerador.nombreLegible();
        }
        return acelerador.nombreLegible() + " · " + dispositivo;
    }
}
