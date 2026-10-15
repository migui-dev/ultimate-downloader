package com.ultimatedownloader.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Guarda una captura de la ventana en un PNG, para revisar el aspecto sin mirarlo a ojo. La
 * conversión se hace a mano para no tener que añadir el módulo javafx.swing.
 */
public final class Captura {

    private static final Logger log = LoggerFactory.getLogger(Captura.class);

    private Captura() {
    }

    public static void guardar(Scene escena, Path destino) {
        try {
            WritableImage imagen = escena.snapshot(null);
            int ancho = (int) imagen.getWidth();
            int alto = (int) imagen.getHeight();
            BufferedImage copia = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_ARGB);
            PixelReader lector = imagen.getPixelReader();
            for (int y = 0; y < alto; y++) {
                for (int x = 0; x < ancho; x++) {
                    copia.setRGB(x, y, lector.getArgb(x, y));
                }
            }
            Files.createDirectories(destino.toAbsolutePath().getParent());
            ImageIO.write(copia, "png", destino.toFile());
            log.info("Captura guardada en {}", destino.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            log.warn("No se ha podido guardar la captura: {}", e.getMessage());
        }
    }
}
