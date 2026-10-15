package com.ultimatedownloader.ui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deja cualquier ventana con el mismo aspecto: icono, tema y barra de título. Las secundarias
 * no heredan nada de la principal, así que sin esto salen con el icono de Java y la barra
 * clara aunque la aplicación esté en oscuro.
 */
public final class Ventanas {

    private static final Logger log = LoggerFactory.getLogger(Ventanas.class);

    /** Tamaños del icono que se cargan; Windows elige el que le viene bien a cada sitio. */
    private static final int[] TAMANOS_ICONO = {16, 24, 32, 48, 64, 128, 256};

    private static List<Image> iconos;

    private Ventanas() {
    }

    /**
     * Prepara una ventana: icono, hoja de estilos del tema y barra de título a juego.
     */
    public static void preparar(Stage ventana, boolean oscuro) {
        if (ventana == null) {
            return;
        }
        ventana.getIcons().setAll(iconos());
        Scene escena = ventana.getScene();
        if (escena != null) {
            Temas.preparar(escena, oscuro);
        }
        tenirAlMostrar(ventana, oscuro);
    }

    /**
     * Lo mismo para un diálogo. Hay que llamarlo justo antes de mostrarlo: su escena no existe
     * hasta que se ha montado el contenido.
     */
    public static void preparar(Dialog<?> dialogo, boolean oscuro) {
        if (dialogo == null) {
            return;
        }
        Scene escena = dialogo.getDialogPane().getScene();
        if (escena == null) {
            return;
        }
        Temas.preparar(escena, oscuro);
        if (escena.getWindow() instanceof Stage ventana) {
            ventana.getIcons().setAll(iconos());
            tenirAlMostrar(ventana, oscuro);
        }
    }

    /**
     * Tiñe la barra de título, ahora si la ventana ya está en pantalla y si no cuando aparezca: el
     * truco de Windows necesita el identificador nativo, que antes no existe.
     */
    private static void tenirAlMostrar(Stage ventana, boolean oscuro) {
        if (ventana.isShowing()) {
            BarraTitulo.aplicar(ventana, oscuro);
        } else {
            ventana.addEventHandler(WindowEvent.WINDOW_SHOWN,
                    evento -> BarraTitulo.aplicar(ventana, oscuro));
        }
    }

    /**
     * Cambia el tema de todas las ventanas abiertas a la vez. Con los ajustes abiertos hay dos en
     * pantalla y antes solo se enteraba una.
     */
    public static void aplicarTemaATodas(boolean oscuro) {
        Temas.aplicar(null, oscuro);
        for (Window ventana : List.copyOf(Window.getWindows())) {
            if (!ventana.isShowing()) {
                continue;
            }
            Temas.marcar(ventana.getScene(), oscuro);
            if (ventana instanceof Stage escenario) {
                BarraTitulo.aplicar(escenario, oscuro);
            }
        }
    }

    /** Las imágenes del icono, cargadas una sola vez. */
    private static synchronized List<Image> iconos() {
        if (iconos != null) {
            return iconos;
        }
        List<Image> cargadas = new ArrayList<>();
        for (int tamano : TAMANOS_ICONO) {
            String ruta = "/com/ultimatedownloader/img/icono-" + tamano + ".png";
            try (InputStream entrada = Ventanas.class.getResourceAsStream(ruta)) {
                if (entrada != null) {
                    cargadas.add(new Image(entrada));
                }
            } catch (Exception e) {
                log.debug("No se ha podido cargar {}: {}", ruta, e.getMessage());
            }
        }
        if (cargadas.isEmpty()) {
            log.warn("No se ha encontrado ningún icono de la aplicación");
        }
        iconos = List.copyOf(cargadas);
        return iconos;
    }
}
