package com.ultimatedownloader.service;

/**
 * Qué ha pasado al intentar leer las cookies configuradas.
 *
 * El mensaje ya viene en el idioma de la interfaz y listo para enseñarse tal cual, sea para
 * decir que todo va bien o para explicar qué hay que hacer.
 */
public record ComprobacionCookies(boolean correcto, String mensaje) {

    public static ComprobacionCookies bien(String mensaje) {
        return new ComprobacionCookies(true, mensaje);
    }

    public static ComprobacionCookies mal(String mensaje) {
        return new ComprobacionCookies(false, mensaje);
    }
}
