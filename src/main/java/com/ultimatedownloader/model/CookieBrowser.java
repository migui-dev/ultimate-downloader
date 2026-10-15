package com.ultimatedownloader.model;

import com.ultimatedownloader.util.Textos;

/**
 * De dónde saca yt-dlp las cookies, para los vídeos con restricción de edad o que piden
 * sesión. Opera GX usa su propio perfil, de ahí la sintaxis opera:<ruta>.
 *
 * El archivo cookies.txt es la opción que funciona siempre: Chrome cifra su base de datos
 * desde la versión 127 y ningún programa puede leerla, y los demás navegadores la bloquean
 * mientras están abiertos.
 */
public enum CookieBrowser {

    NINGUNO("No usar cookies", null),
    ARCHIVO("Archivo cookies.txt", "archivo"),
    CHROME("Google Chrome", "chrome"),
    EDGE("Microsoft Edge", "edge"),
    FIREFOX("Mozilla Firefox", "firefox"),
    OPERA("Opera", "opera"),
    OPERA_GX("Opera GX", "opera"),
    BRAVE("Brave", "brave"),
    VIVALDI("Vivaldi", "vivaldi"),
    CHROMIUM("Chromium", "chromium");

    private final String nombreLegible;
    private final String codigoYtDlp;

    CookieBrowser(String nombreLegible, String codigoYtDlp) {
        this.nombreLegible = nombreLegible;
        this.codigoYtDlp = codigoYtDlp;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("navegador", name(), nombreLegible);
    }

    /** Nombre que entiende yt-dlp, o null si no hay que usar cookies. */
    public String codigoYtDlp() {
        return codigoYtDlp;
    }

    public boolean activo() {
        return codigoYtDlp != null;
    }

    /** true si las cookies salen de un archivo y no de un navegador instalado. */
    public boolean usaArchivo() {
        return this == ARCHIVO;
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
