package com.ultimatedownloader.service;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Rutas de la aplicación. Todo lo que hay que escribir vive bajo la carpeta de datos de la aplicación:
 * en Program Files un usuario normal no puede escribir, y «yt-dlp -U» necesita sobrescribirse.
 *
 * Aquí no se registra nada con SLF4J: Launcher usa esta clase antes de que arranque Logback.
 */
public final class AppPaths {

    private static final String NOMBRE_APLICACION = "UltimateDownloader";

    private AppPaths() {
    }

    /**
     * Donde la aplicación guarda lo suyo, siguiendo la costumbre de cada sistema: %APPDATA% en
     * Windows, ~/Library/Application Support en macOS y XDG_DATA_HOME en Linux.
     */
    public static Path carpetaDatos() {
        return baseDeDatos().resolve(carpetaSegunSistema());
    }

    private static Path baseDeDatos() {
        return switch (Plataforma.actual()) {
            case WINDOWS -> {
                Path base = rutaDeVariable("APPDATA");
                yield base != null ? base : carpetaUsuario().resolve("AppData").resolve("Roaming");
            }
            case MAC -> carpetaUsuario().resolve("Library").resolve("Application Support");
            case LINUX -> {
                Path base = rutaDeVariable("XDG_DATA_HOME");
                yield base != null ? base : carpetaUsuario().resolve(".local").resolve("share");
            }
        };
    }

    /**
     * En Windows y macOS la carpeta lleva el nombre de la aplicación; en Linux lo habitual es
     * una carpeta oculta en minúsculas.
     */
    private static String carpetaSegunSistema() {
        return Plataforma.esLinux() ? "ultimate-downloader" : NOMBRE_APLICACION;
    }

    /** Carpeta del registro detallado. */
    public static Path carpetaLogs() {
        return carpetaDatos().resolve("logs");
    }

    /** Fichero JSON de ajustes persistentes. */
    public static Path ficheroAjustes() {
        return carpetaDatos().resolve("ajustes.json");
    }

    /** Carpeta escribible donde se guardan yt-dlp y ffmpeg. */
    public static Path carpetaHerramientas() {
        return carpetaDatos().resolve("herramientas");
    }

    /**
     * Carpeta con los binarios que vienen dentro del paquete, o null si se está ejecutando
     * desde el código. La ruta la pone el lanzador de jpackage al arrancar: en el empaquetado
     * se le pasa «-Dud.herramientas=$APPDIR/herramientas», que cada sistema resuelve a un sitio
     * distinto dentro de la instalación.
     */
    public static Path carpetaHerramientasIncluidas() {
        return rutaDePropiedad("ud.herramientas");
    }

    /** Carpeta donde se descomprimen paquetes temporales (ffmpeg viene en un ZIP). */
    public static Path carpetaTemporal() {
        return carpetaDatos().resolve("temporal");
    }

    /**
     * Carpeta de instalación de la aplicación, si se está ejecutando desde una imagen de
     * jpackage. Se usa solo para buscar binarios que vengan junto a la aplicación.
     */
    public static Path carpetaAplicacion() {
        Path lanzador = rutaDePropiedad("jpackage.app-path");
        if (lanzador != null && lanzador.getParent() != null) {
            return lanzador.getParent();
        }
        return Paths.get("").toAbsolutePath();
    }

    /** Carpeta de descargas del usuario; si no existe, el perfil del usuario. */
    public static Path carpetaDescargasPorDefecto() {
        Path configurada = rutaDeVariable("XDG_DOWNLOAD_DIR");
        if (configurada != null && Files.isDirectory(configurada)) {
            return configurada;
        }
        for (String nombre : List.of("Downloads", "Descargas")) {
            Path candidata = carpetaUsuario().resolve(nombre);
            if (Files.isDirectory(candidata)) {
                return candidata;
            }
        }
        return carpetaUsuario();
    }

    private static Path carpetaUsuario() {
        Path perfil = rutaDePropiedad("user.home");
        if (perfil != null) {
            return perfil;
        }
        Path desdeVariable = rutaDeVariable(Plataforma.esWindows() ? "USERPROFILE" : "HOME");
        return desdeVariable != null ? desdeVariable : Paths.get(".").toAbsolutePath();
    }

    private static Path rutaDeVariable(String nombre) {
        return aRuta(System.getenv(nombre));
    }

    private static Path rutaDePropiedad(String nombre) {
        return aRuta(System.getProperty(nombre));
    }

    private static Path aRuta(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Paths.get(valor);
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
