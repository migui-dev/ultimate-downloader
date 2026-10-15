package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.model.Idioma;
import com.ultimatedownloader.util.Textos;
import java.nio.file.Path;
import java.util.Arrays;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Propiedades -Dud.* para revisar la interfaz sin ir pulsando a mano: ventana, urlInicial,
 * archivos, separar, instalarEntorno, cambiarIdioma, abrir, capturaEn y cerrarAlArrancar.
 *
 * No son opciones de la aplicación, son para el desarrollo, y Gradle las traduce desde
 * -Pcaptura=…, -Pabrir=… y compañía. Si no se pasa ninguna, esto no hace nada.
 */
public final class OpcionesDesarrollo {

    private final Contexto contexto;
    private final Scene escena;
    private final ShellController armazon;

    public OpcionesDesarrollo(Contexto contexto, Scene escena, ShellController armazon) {
        this.contexto = contexto;
        this.escena = escena;
        this.armazon = armazon;
    }

    public void aplicar() {
        tamanoDeVentana();
        enlaceInicial();
        archivosAConvertir();
        separacionInicial();
        asistenteDelMotor();
        cambioDeIdioma();
        pantallaInicial();
        captura();
        cierreInmediato();
    }

    private void tamanoDeVentana() {
        String tamano = propiedad("ud.ventana");
        if (tamano == null || !tamano.matches("\\d+x\\d+")) {
            return;
        }
        String[] partes = tamano.split("x");
        escena.getWindow().setWidth(Double.parseDouble(partes[0]));
        escena.getWindow().setHeight(Double.parseDouble(partes[1]));
    }

    private void enlaceInicial() {
        String url = propiedad("ud.urlInicial");
        if (url != null && armazon.moduloDescargar() != null) {
            armazon.moduloDescargar().cargarUrlInicial(url);
        }
    }

    private void archivosAConvertir() {
        String archivos = propiedad("ud.archivos");
        if (archivos != null && armazon.moduloConvertir() != null) {
            armazon.moduloConvertir().anadirArchivos(
                    Arrays.stream(archivos.split(";")).map(Path::of).toList());
        }
    }

    private void separacionInicial() {
        String cancion = propiedad("ud.separar");
        if (cancion != null && armazon.moduloSeparar() != null) {
            armazon.moduloSeparar().separarAlArrancar(Path.of(cancion));
        }
    }

    private void asistenteDelMotor() {
        if (Boolean.getBoolean("ud.instalarEntorno") && armazon.moduloSeparar() != null) {
            armazon.moduloSeparar().instalarAlArrancar();
        }
    }

    /** Cambia de idioma en caliente, para ver que la interfaz se rehace entera sin reiniciar. */
    private void cambioDeIdioma() {
        String idioma = propiedad("ud.cambiarIdioma");
        if (idioma == null) {
            return;
        }
        trasUnosSegundos(4, () -> {
            Textos.usar(Idioma.desdeCodigo(idioma));
            contexto.recargarInterfaz();
        });
    }

    private void pantallaInicial() {
        String pantalla = propiedad("ud.abrir");
        if (pantalla != null) {
            Platform.runLater(() -> armazon.abrirPantalla(pantalla));
        }
    }

    /** Abre la ventana y la cierra: sirve para comprobar que arranca sin tocar nada. */
    private void cierreInmediato() {
        if (Boolean.getBoolean("ud.cerrarAlArrancar")) {
            trasUnosSegundos(2, Platform::exit);
        }
    }

    private void captura() {
        String destino = propiedad("ud.capturaEn");
        if (destino == null) {
            return;
        }
        trasUnosSegundos(Integer.getInteger("ud.capturaTrasSegundos", 3), () -> {
            Captura.guardar(escenaDeArriba(), Path.of(destino));
            // Un diálogo modal abierto tiene su propio bucle de eventos: hay que cerrarlo
            // antes, o Platform.exit() no termina limpiamente.
            Window.getWindows().stream()
                    .filter(Window::isShowing)
                    .toList()
                    .reversed()
                    .forEach(Window::hide);
            Platform.exit();
        });
    }

    /** La escena de la ventana que está encima, para capturar también los diálogos. */
    private Scene escenaDeArriba() {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .reduce((primera, ultima) -> ultima)
                .map(Window::getScene)
                .orElse(escena);
    }

    private static void trasUnosSegundos(int segundos, Runnable accion) {
        PauseTransition espera = new PauseTransition(Duration.seconds(segundos));
        espera.setOnFinished(e -> accion.run());
        espera.play();
    }

    private static String propiedad(String nombre) {
        String valor = System.getProperty(nombre);
        return valor != null && !valor.isBlank() ? valor : null;
    }
}
