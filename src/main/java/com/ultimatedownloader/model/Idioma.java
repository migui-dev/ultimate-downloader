package com.ultimatedownloader.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Idiomas en los que está la aplicación. Cada uno se llama a sí mismo en su propio idioma
 * («Deutsch», no «Alemán»), que es lo que busca quien abre la lista sin entender la actual.
 *
 * Añadir uno es crear textos_<código>.properties y una línea aquí.
 */
public enum Idioma {

    /** El del sistema operativo; si no está entre los que hay, español. */
    SISTEMA(null, null),

    ESPANOL("es", "Español"),
    INGLES("en", "English"),
    FRANCES("fr", "Français"),
    PORTUGUES("pt", "Português"),
    ALEMAN("de", "Deutsch"),
    ITALIANO("it", "Italiano"),
    RUSO("ru", "Русский"),
    POLACO("pl", "Polski");

    /** El de la aplicación cuando no se puede averiguar nada mejor. */
    public static final Idioma PREDETERMINADO = ESPANOL;

    private final String codigo;
    private final String nombrePropio;

    Idioma(String codigo, String nombrePropio) {
        this.codigo = codigo;
        this.nombrePropio = nombrePropio;
    }

    /** Código ISO 639-1, o null en SISTEMA. */
    public String codigo() {
        return codigo;
    }

    /** Cómo se llama este idioma en sí mismo, o null en SISTEMA. */
    public String nombrePropio() {
        return nombrePropio;
    }

    public Locale locale() {
        return codigo != null ? Locale.forLanguageTag(codigo) : Locale.getDefault();
    }

    /** Los que se pueden elegir de verdad, sin la opción «como el sistema». */
    public static List<Idioma> traducidos() {
        return Arrays.stream(values()).filter(i -> i.codigo != null).toList();
    }

    /** Lee el valor guardado en los ajustes; lo que no se reconozca vuelve a SISTEMA. */
    public static Idioma desdeCodigo(String codigo) {
        if (codigo == null || codigo.isBlank() || "sistema".equalsIgnoreCase(codigo)) {
            return SISTEMA;
        }
        return Arrays.stream(values())
                .filter(i -> codigo.equalsIgnoreCase(i.codigo))
                .findFirst()
                .orElse(SISTEMA);
    }

    /**
     * SISTEMA se resuelve mirando el idioma de Windows; si no está traducido, español. Se compara
     * solo la parte del idioma, así que un «pt-BR» entra por «pt».
     */
    public Idioma efectivo() {
        if (this != SISTEMA) {
            return this;
        }
        String delSistema = Locale.getDefault().getLanguage();
        return traducidos().stream()
                .filter(i -> i.codigo.equals(delSistema))
                .findFirst()
                .orElse(PREDETERMINADO);
    }
}
