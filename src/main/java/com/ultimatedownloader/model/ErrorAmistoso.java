package com.ultimatedownloader.model;

/**
 * Error listo para mostrar: explicado en español y sin rastro de la excepción. El detalle es
 * el texto técnico y va al registro, nunca a la pantalla.
 */
public record ErrorAmistoso(String mensaje, String sugerencia, String detalle) {

    public ErrorAmistoso(String mensaje, String sugerencia) {
        this(mensaje, sugerencia, null);
    }

    /** Mensaje y sugerencia juntos, para un cuadro de diálogo o la celda de la cola. */
    public String textoCompleto() {
        return sugerencia == null || sugerencia.isBlank() ? mensaje : mensaje + " " + sugerencia;
    }
}
