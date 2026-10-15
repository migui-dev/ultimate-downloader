package com.ultimatedownloader.service;

import com.ultimatedownloader.util.Formatos;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Comprueba que una carpeta sirve: que existe, que se puede escribir y que queda sitio.
 *
 * No se usa Files.isWritable porque en Windows no mira todas las listas de control de acceso:
 * la única forma fiable de saber si se puede escribir es intentarlo.
 */
public final class FolderValidator {

    /** Margen que se deja libre además del tamaño estimado de la descarga. */
    public static final long MARGEN_BYTES = 200L * 1024 * 1024;

    private FolderValidator() {
    }

    /** Si se puede descargar ahí, y si no, qué pasa, explicado en español. */
    public record Resultado(boolean valida, String mensaje) {

        public static final Resultado CORRECTO = new Resultado(true, null);

        public static Resultado error(String mensaje) {
            return new Resultado(false, mensaje);
        }
    }

    public static Resultado validar(Path carpeta, long bytesNecesarios) {
        if (carpeta == null || carpeta.toString().isBlank()) {
            return Resultado.error(Textos.t("carpeta.elige_una"));
        }
        if (!Files.exists(carpeta)) {
            return Resultado.error(Textos.t("carpeta.no_existe", carpeta));
        }
        if (!Files.isDirectory(carpeta)) {
            return Resultado.error(Textos.t("carpeta.no_es_carpeta", carpeta));
        }
        if (!sePuedeEscribir(carpeta)) {
            return Resultado.error(Textos.t("carpeta.sin_permisos", carpeta));
        }
        long necesario = bytesNecesarios > 0 ? bytesNecesarios + MARGEN_BYTES : 0;
        if (necesario > 0) {
            long libre = espacioLibre(carpeta);
            if (libre > 0 && libre < necesario) {
                return Resultado.error(Textos.t("carpeta.sin_espacio",
                        Formatos.tamano(necesario), Formatos.tamano(libre)));
            }
        }
        return Resultado.CORRECTO;
    }

    /** Intenta crear un archivo temporal para saber si de verdad se puede escribir. */
    public static boolean sePuedeEscribir(Path carpeta) {
        Path prueba = null;
        try {
            prueba = Files.createTempFile(carpeta, ".descargador", ".tmp");
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        } finally {
            if (prueba != null) {
                try {
                    Files.deleteIfExists(prueba);
                } catch (IOException ignorado) {
                    // Si no se puede borrar el archivo de prueba, no es un problema grave.
                }
            }
        }
    }

    /** Espacio libre del volumen, o 0 si no se puede averiguar. */
    public static long espacioLibre(Path carpeta) {
        try {
            FileStore volumen = Files.getFileStore(carpeta);
            return volumen.getUsableSpace();
        } catch (IOException | SecurityException e) {
            return 0;
        }
    }

    /** Crea la carpeta (y las que falten por encima) si no existe. */
    public static boolean crearSiNoExiste(Path carpeta) {
        if (carpeta == null) {
            return false;
        }
        if (Files.isDirectory(carpeta)) {
            return true;
        }
        try {
            Files.createDirectories(carpeta);
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }
}
