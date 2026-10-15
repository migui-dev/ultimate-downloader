package com.ultimatedownloader.model.separacion;

import com.ultimatedownloader.util.Textos;
import java.util.Locale;

/**
 * Familia de red de un modelo. Importa porque cada una tiene sus propias opciones avanzadas y
 * porque no todas corren igual de bien en cada dispositivo.
 */
public enum Arquitectura {

    /** La más antigua de UVR; rápida y con muchos modelos especializados. */
    VR("VR", "Rápida y con muchos modelos de propósito concreto"),

    /** MDX-Net clásico, en formato ONNX. */
    MDX("MDX-Net", "Equilibrio entre velocidad y calidad"),

    /** MDX23C y los Roformer: los de mejor calidad hoy. */
    MDXC("MDX23C y Roformer", "La mejor calidad; tarda algo más"),

    /** Demucs de Meta: separa en cuatro pistas o más. */
    DEMUCS("Demucs", "Separa en varias pistas: voz, batería, bajo y resto");

    private final String nombre;
    private final String descripcion;

    Arquitectura(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String nombreLegible() {
        return Textos.deEnum("arquitectura", name(), nombre);
    }

    public String descripcion() {
        return Textos.deEnum("arquitectura_desc", name(), descripcion);
    }

    /** Traduce la clave que usa --list_models; lo desconocido se trata como MDX. */
    public static Arquitectura desdeClave(String clave) {
        if (clave == null) {
            return MDX;
        }
        return switch (clave.toUpperCase(Locale.ROOT)) {
            case "VR" -> VR;
            case "MDXC" -> MDXC;
            case "DEMUCS" -> DEMUCS;
            default -> MDX;
        };
    }
}
