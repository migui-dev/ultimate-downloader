package com.ultimatedownloader.ui;

import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.service.Plataforma;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Avisos en español y apertura de carpetas en el explorador. En los errores se ve el mensaje y
 * la sugerencia; el detalle técnico queda plegado, nunca a la vista de entrada.
 */
public final class Dialogos {

    private static final Logger log = LoggerFactory.getLogger(Dialogos.class);

    private Dialogos() {
    }

    public static void error(Window propietario, String titulo, ErrorAmistoso error) {
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.initOwner(propietario);
        alerta.setTitle(titulo);
        alerta.setHeaderText(error.mensaje());
        alerta.setContentText(error.sugerencia());
        alerta.getDialogPane().setMinWidth(540);

        if (error.detalle() != null && !error.detalle().isBlank()) {
            TextArea texto = new TextArea(error.detalle());
            texto.setEditable(false);
            texto.setWrapText(true);
            texto.setPrefRowCount(8);
            TitledPane plegable = new TitledPane(Textos.t("comun.detalle_tecnico"), texto);
            plegable.setExpanded(false);
            alerta.getDialogPane().setExpandableContent(plegable);
        }
        Ventanas.preparar(alerta, Temas.esOscuro());
        alerta.showAndWait();
    }

    public static void informacion(Window propietario, String titulo, String mensaje, String detalle) {
        Alert alerta = new Alert(Alert.AlertType.INFORMATION);
        alerta.initOwner(propietario);
        alerta.setTitle(titulo);
        alerta.setHeaderText(mensaje);
        alerta.setContentText(detalle);
        alerta.getDialogPane().setMinWidth(500);
        Ventanas.preparar(alerta, Temas.esOscuro());
        alerta.showAndWait();
    }

    /** Pregunta de sí o no, con los botones etiquetados en español. */
    public static boolean confirmar(Window propietario, String titulo, String mensaje,
                                    String textoSi, String textoNo) {
        ButtonType si = new ButtonType(textoSi, ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType(textoNo, ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, mensaje, si, no);
        alerta.initOwner(propietario);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.getDialogPane().setMinWidth(500);
        Ventanas.preparar(alerta, Temas.esOscuro());
        Optional<ButtonType> respuesta = alerta.showAndWait();
        return respuesta.isPresent() && respuesta.get() == si;
    }

    /**
     * Abre una carpeta, o la abre dejando un archivo seleccionado. Se llama a explorer.exe en vez
     * de usar java.awt.Desktop para no arrancar AWT dentro de una aplicación JavaFX.
     */
    public static void abrirEnExplorador(Path carpeta, Path archivo) {
        String abridor = Plataforma.abridorDeCarpetas();
        List<String> orden;
        if (archivo != null && Files.exists(archivo) && seleccionaArchivos()) {
            orden = seleccionar(abridor, archivo);
        } else if (carpeta != null && Files.isDirectory(carpeta)) {
            orden = List.of(abridor, carpeta.toAbsolutePath().toString());
        } else if (archivo != null && Files.exists(archivo)) {
            orden = List.of(abridor, archivo.toAbsolutePath().getParent().toString());
        } else {
            log.warn("No hay nada que abrir: carpeta={}, archivo={}", carpeta, archivo);
            return;
        }
        try {
            new ProcessBuilder(orden).start();
        } catch (IOException e) {
            log.warn("No se ha podido abrir el explorador: {}", e.getMessage());
        }
    }

    /** Solo Windows y macOS saben abrir la carpeta con un archivo ya seleccionado. */
    private static boolean seleccionaArchivos() {
        return Plataforma.esWindows() || Plataforma.esMac();
    }

    private static List<String> seleccionar(String abridor, Path archivo) {
        String ruta = archivo.toAbsolutePath().toString();
        // explorer.exe espera «/select,<ruta>» como un único argumento; open usa «-R».
        return Plataforma.esWindows()
                ? List.of(abridor, "/select," + ruta)
                : List.of(abridor, "-R", ruta);
    }
}
