package com.ultimatedownloader.service.consola;

/** Gravedad de una línea de la consola; determina su color. */
public enum NivelConsola {

    INFO("consola-info"),
    AVISO("consola-aviso"),
    ERROR("consola-error");

    private final String claseEstilo;

    NivelConsola(String claseEstilo) {
        this.claseEstilo = claseEstilo;
    }

    public String claseEstilo() {
        return claseEstilo;
    }

    /**
     * Deduce el nivel del texto de la línea. Los tres programas escriben en inglés y sin formato
     * fijo, pero marcan los errores y los avisos al principio de forma reconocible.
     */
    public static NivelConsola deTexto(String linea) {
        if (linea == null) {
            return INFO;
        }
        String l = linea.strip().toLowerCase(java.util.Locale.ROOT);
        if (l.startsWith("error") || l.contains("error:") || l.startsWith("traceback")
                || l.contains("exception")) {
            return ERROR;
        }
        if (l.startsWith("warning") || l.contains("warning:") || l.startsWith("[warning]")
                || l.contains("deprecated")) {
            return AVISO;
        }
        return INFO;
    }
}
