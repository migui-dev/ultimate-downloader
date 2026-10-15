package com.ultimatedownloader.service;

import java.util.Locale;

/**
 * El sistema en el que se está ejecutando y lo poco que cambia de uno a otro: cómo se llaman
 * los ejecutables, dónde van los datos del usuario y con qué se abre una carpeta.
 *
 * Todo lo que dependa del sistema pasa por aquí. Así el resto del código no tiene que
 * preguntar por «os.name» ni acordarse de que en Windows los programas acaban en .exe.
 */
public enum Plataforma {

    WINDOWS, LINUX, MAC;

    private static final Plataforma ACTUAL = detectar();

    public static Plataforma actual() {
        return ACTUAL;
    }

    public static boolean esWindows() {
        return ACTUAL == WINDOWS;
    }

    public static boolean esMac() {
        return ACTUAL == MAC;
    }

    public static boolean esLinux() {
        return ACTUAL == LINUX;
    }

    /** Añade «.exe» en Windows y deja el nombre tal cual en los demás. */
    public static String ejecutable(String nombre) {
        return esWindows() ? nombre + ".exe" : nombre;
    }

    /** Carpeta del entorno virtual de Python donde viven sus programas. */
    public static String carpetaBinariosPython() {
        return esWindows() ? "Scripts" : "bin";
    }

    /** Programa que abre una carpeta en el explorador de archivos del sistema. */
    public static String abridorDeCarpetas() {
        return switch (ACTUAL) {
            case WINDOWS -> "explorer.exe";
            case MAC -> "open";
            case LINUX -> "xdg-open";
        };
    }

    /** Separador de rutas del PATH: punto y coma en Windows, dos puntos en el resto. */
    public static String separadorDePath() {
        return esWindows() ? ";" : ":";
    }

    /** Nombre para los mensajes y para el registro. */
    public String nombreLegible() {
        return switch (this) {
            case WINDOWS -> "Windows";
            case MAC -> "macOS";
            case LINUX -> "Linux";
        };
    }

    /** true en los procesadores ARM: los Apple Silicon y algunos Linux. */
    public static boolean esArm() {
        String arco = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return arco.contains("aarch64") || arco.contains("arm");
    }

    /** Identificador que usan los artefactos de JavaFX y las descargas de uv. */
    public String clave() {
        return switch (this) {
            case WINDOWS -> "win";
            case MAC -> "mac";
            case LINUX -> "linux";
        };
    }

    private static Plataforma detectar() {
        String nombre = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (nombre.contains("win")) {
            return WINDOWS;
        }
        if (nombre.contains("mac") || nombre.contains("darwin")) {
            return MAC;
        }
        return LINUX;
    }
}
