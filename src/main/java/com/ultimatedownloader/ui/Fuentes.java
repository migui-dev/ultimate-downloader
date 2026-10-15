package com.ultimatedownloader.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Carga las tipografías empaquetadas: Inter para la interfaz y JetBrains Mono para los datos.
 * Van dentro para que la aplicación se vea igual en cualquier equipo.
 *
 * JavaFX no deja activar características OpenType, así que no hay cifras tabulares en Inter.
 * Por eso los números de las tablas usan la monoespaciada: así no bailan al actualizarse.
 */
public final class Fuentes {

    private static final Logger log = LoggerFactory.getLogger(Fuentes.class);

    private static final List<String> FICHEROS = List.of(
            "Inter-Regular.ttf",
            "Inter-Medium.ttf",
            "Inter-SemiBold.ttf",
            "JetBrainsMono-Regular.ttf",
            "JetBrainsMono-Medium.ttf");

    private static boolean cargadas;

    private Fuentes() {
    }

    /** Registra las tipografías en JavaFX. Es idempotente. */
    public static void cargar() {
        if (cargadas) {
            return;
        }
        for (String fichero : FICHEROS) {
            String ruta = "/com/ultimatedownloader/fonts/" + fichero;
            // Se carga por flujo y no por URL: la variante de URL devuelve null cuando la ruta
            // de la aplicación tiene espacios, porque JavaFX no deshace el %20.
            try (InputStream entrada = Fuentes.class.getResourceAsStream(ruta)) {
                if (entrada == null) {
                    log.warn("No se encuentra la tipografía {}", ruta);
                    continue;
                }
                Font fuente = Font.loadFont(entrada, 13);
                if (fuente == null) {
                    log.warn("JavaFX no ha podido cargar {}", fichero);
                } else {
                    log.debug("Cargada {} como «{}» (familia {})",
                            fichero, fuente.getName(), fuente.getFamily());
                }
            } catch (IOException e) {
                log.warn("No se ha podido leer la tipografía {}: {}", fichero, e.getMessage());
            }
        }
        cargadas = true;
        log.info("Tipografías disponibles: Inter={}, JetBrains Mono={}",
                Font.getFamilies().contains("Inter"),
                Font.getFamilies().contains("JetBrains Mono"));
    }

    /** Familias registradas que empiezan por un prefijo; se usa para depurar. */
    public static List<String> familias(String prefijo) {
        return Font.getFamilies().stream().filter(f -> f.startsWith(prefijo)).toList();
    }
}
