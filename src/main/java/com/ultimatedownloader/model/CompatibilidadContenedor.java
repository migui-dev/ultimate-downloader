package com.ultimatedownloader.model;

/** Si el contenedor elegido admite los códecs. El aviso y la sugerencia van a null si encaja. */
public record CompatibilidadContenedor(boolean compatible, String aviso, String sugerencia) {

    public static final CompatibilidadContenedor COMPATIBLE =
            new CompatibilidadContenedor(true, null, null);
}
