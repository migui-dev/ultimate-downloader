package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.service.FolderValidator;
import com.ultimatedownloader.ui.iconos.Iconos;
import com.ultimatedownloader.ui.iconos.Lucide;
import com.ultimatedownloader.util.Formatos;
import com.ultimatedownloader.util.Textos;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.LongSupplier;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * Bloque de carpeta de destino que comparten los tres módulos: la ruta, las carpetas recientes,
 * el espacio libre y los botones de cambiar y abrir.
 */
public final class CarpetaDestinoController {

    private final Contexto contexto;

    private final ObjectProperty<Path> carpeta = new SimpleObjectProperty<>();

    /** Lo que va a ocupar lo que se guarde ahí, para avisar antes de quedarse sin sitio. */
    private LongSupplier estimacion = () -> 0;

    private String tituloDelSelector = Textos.t("comun.elige_la_carpeta_de_destino");
    private String avisoSiNoExiste = Textos.t("comun.carpeta_no_valida");

    @FXML private Label etiquetaCarpeta;
    @FXML private Label etiquetaEspacio;
    @FXML private MenuButton menuRecientes;
    @FXML private Button botonCambiar;
    @FXML private Button botonAbrir;

    public CarpetaDestinoController(Contexto contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        botonCambiar.setGraphic(Iconos.de(Lucide.CARPETA));
        botonAbrir.setGraphic(Iconos.de(Lucide.ABRIR_FUERA));
        carpeta.addListener((obs, antes, ahora) -> refrescar());
        carpeta.set(contexto.carpetaDestino());
        refrescarRecientes();
    }

    public ObjectProperty<Path> carpetaProperty() {
        return carpeta;
    }

    public Path carpeta() {
        return carpeta.get();
    }

    /** Pone la carpeta sin preguntar ni validar; para volver de los ajustes, por ejemplo. */
    public void usar(Path nueva) {
        carpeta.set(nueva);
    }

    /** Textos propios del módulo: el título del selector y el aviso de carpeta inexistente. */
    public void textos(String tituloDelSelector, String avisoSiNoExiste) {
        this.tituloDelSelector = tituloDelSelector;
        this.avisoSiNoExiste = avisoSiNoExiste;
        refrescarEspacio();
    }

    /** De dónde sacar los bytes que hacen falta; se consulta al refrescar el espacio. */
    public void estimacion(LongSupplier bytes) {
        this.estimacion = bytes != null ? bytes : () -> 0;
        refrescarEspacio();
    }

    /** Vuelve a mirar el espacio libre: la estimación cambia según lo que se vaya a guardar. */
    public void refrescarEspacio() {
        Path destino = carpeta.get();
        if (destino == null || !Files.isDirectory(destino)) {
            aviso(avisoSiNoExiste);
            return;
        }
        FolderValidator.Resultado resultado = FolderValidator.validar(destino, estimacion.getAsLong());
        if (resultado.valida()) {
            etiquetaEspacio.setText(Textos.t("comun.espacio_libre") + " "
                    + Formatos.tamano(FolderValidator.espacioLibre(destino)));
            etiquetaEspacio.getStyleClass().setAll("texto-3");
        } else {
            aviso(resultado.mensaje());
        }
    }

    private void aviso(String mensaje) {
        etiquetaEspacio.setText(mensaje);
        etiquetaEspacio.getStyleClass().setAll("mensaje-aviso");
    }

    @FXML
    private void cambiar() {
        DirectoryChooser selector = new DirectoryChooser();
        selector.setTitle(tituloDelSelector);
        Path actual = carpeta.get();
        if (actual != null && Files.isDirectory(actual)) {
            selector.setInitialDirectory(actual.toFile());
        }
        File elegida = selector.showDialog(ventana());
        if (elegida != null) {
            elegir(elegida.toPath());
        }
    }

    @FXML
    private void abrir() {
        Dialogos.abrirEnExplorador(carpeta.get(), null);
    }

    private void elegir(Path nueva) {
        FolderValidator.Resultado resultado = FolderValidator.validar(nueva, 0);
        if (!resultado.valida()) {
            Dialogos.error(ventana(), Textos.t("comun.carpeta_no_valida"),
                    new ErrorAmistoso(resultado.mensaje(), Textos.t("comun.elige_otra_carpeta")));
            return;
        }
        carpeta.set(nueva);
        contexto.recordarCarpeta(nueva);
        refrescarRecientes();
    }

    private void refrescar() {
        Path destino = carpeta.get();
        etiquetaCarpeta.setText(destino != null ? destino.toString() : Textos.t("comun.sin_carpeta"));
        etiquetaCarpeta.setTooltip(new Tooltip(etiquetaCarpeta.getText()));
        botonAbrir.setDisable(destino == null || !Files.isDirectory(destino));
        refrescarEspacio();
    }

    private void refrescarRecientes() {
        menuRecientes.getItems().clear();
        for (String ruta : contexto.ajustes().getCarpetasRecientes()) {
            MenuItem elemento = new MenuItem(ruta);
            elemento.setOnAction(e -> elegir(Path.of(ruta)));
            menuRecientes.getItems().add(elemento);
        }
        menuRecientes.setDisable(menuRecientes.getItems().isEmpty());
    }

    private Window ventana() {
        return etiquetaCarpeta.getScene() != null ? etiquetaCarpeta.getScene().getWindow() : null;
    }
}
