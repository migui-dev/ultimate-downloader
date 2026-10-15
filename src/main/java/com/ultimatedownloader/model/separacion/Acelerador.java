package com.ultimatedownloader.model.separacion;

import com.ultimatedownloader.service.Plataforma;
import com.ultimatedownloader.util.Textos;

/**
 * Cómo va a calcular el motor UVR. No es lo que el usuario pide, sino lo que se puede usar de
 * verdad: la interfaz enseña siempre el valor comprobado contra PyTorch.
 */
public enum Acelerador {

    /** Tarjeta NVIDIA con PyTorch compilado para CUDA. */
    CUDA("CUDA", "Tarjeta NVIDIA"),

    /** DirectML: funciona con tarjetas AMD e Intel, bastante más lento que CUDA. */
    DIRECTML("DirectML", "Tarjeta AMD o Intel"),

    /** Procesador. Funciona siempre, pero una canción puede tardar varios minutos. */
    CPU("CPU", "Procesador");

    private final String nombre;
    private final String descripcion;

    Acelerador(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String nombreLegible() {
        return nombre;
    }

    public String descripcion() {
        return Textos.deEnum("acelerador_desc", name(), descripcion);
    }

    /** Si este sistema puede usar esta opción. DirectML solo existe en Windows. */
    public boolean disponible() {
        return this != DIRECTML || Plataforma.esWindows();
    }

    /** Extra de audio-separator que instala el motor de inferencia correspondiente. */
    public String extraPip() {
        return switch (this) {
            case CUDA -> "gpu";
            case DIRECTML -> "dml";
            case CPU -> "cpu";
        };
    }
}
