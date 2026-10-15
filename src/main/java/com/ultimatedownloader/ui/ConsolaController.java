package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.service.consola.LineaConsola;
import com.ultimatedownloader.ui.iconos.Iconos;
import com.ultimatedownloader.ui.iconos.Lucide;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Panel de consola: lo que escriben los procesos externos, en directo. Cada línea lleva su
 * hora, de qué proceso viene y un color según el nivel.
 */
public final class ConsolaController {

    private static final Logger log = LoggerFactory.getLogger(ConsolaController.class);

    /** Opción «todas» del filtro; de instancia para que siga al idioma elegido. */
    private final String todas = Textos.t("consola.todas_las_tareas");

    private final Contexto contexto;
    private FilteredList<LineaConsola> filtradas;

    @FXML private Label etiquetaCuenta;
    @FXML private ComboBox<String> filtroTarea;
    @FXML private TextField campoBuscar;
    @FXML private Button botonCopiar;
    @FXML private Button botonGuardar;
    @FXML private Button botonLimpiar;
    @FXML private Button botonCerrar;
    @FXML private ListView<LineaConsola> listaLineas;

    private Runnable alCerrar = () -> { };

    public ConsolaController(Contexto contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        botonCopiar.setGraphic(Iconos.de(Lucide.COPIAR));
        botonCopiar.setTooltip(new Tooltip(Textos.t("consola.copiar_todo")));
        botonGuardar.setGraphic(Iconos.de(Lucide.GUARDAR));
        botonGuardar.setTooltip(new Tooltip(Textos.t("consola.guardar_en_un_archivo")));
        botonLimpiar.setGraphic(Iconos.de(Lucide.PAPELERA));
        botonLimpiar.setTooltip(new Tooltip(Textos.t("consola.limpiar")));
        botonCerrar.setGraphic(Iconos.de(Lucide.CERRAR));
        botonCerrar.setTooltip(new Tooltip(Textos.t("consola.cerrar_la_consola")));

        filtradas = new FilteredList<>(contexto.consola().lineas(), l -> true);
        listaLineas.setItems(filtradas);
        listaLineas.setCellFactory(l -> new CeldaLinea());
        // Altura fija: la consola queda compacta como una terminal y, con cinco mil líneas,
        // la lista no tiene que medir cada una para saber dónde está.
        listaLineas.setFixedCellSize(20);
        listaLineas.setPlaceholder(new Label(Textos.t("consola.vacia")));

        filtroTarea.getItems().add(todas);
        filtroTarea.setValue(todas);
        filtroTarea.valueProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        campoBuscar.textProperty().addListener((obs, antes, ahora) -> aplicarFiltro());

        contexto.consola().lineas().addListener(
                (ListChangeListener<LineaConsola>) cambio -> {
                    actualizarTareas();
                    etiquetaCuenta.setText(Textos.t("consola.cuenta_lineas", contexto.consola().lineas().size()));
                    desplazarAlFinal();
                });
    }

    /** Qué hacer cuando el usuario cierra el panel; lo fija el contenedor. */
    public void alCerrar(Runnable accion) {
        this.alCerrar = accion != null ? accion : () -> { };
    }

    private void actualizarTareas() {
        for (String tarea : contexto.consola().tareas()) {
            if (!filtroTarea.getItems().contains(tarea)) {
                filtroTarea.getItems().add(tarea);
            }
        }
    }

    private void aplicarFiltro() {
        String tarea = filtroTarea.getValue();
        String busqueda = campoBuscar.getText() == null
                ? "" : campoBuscar.getText().strip().toLowerCase(Locale.ROOT);

        filtradas.setPredicate(linea -> {
            if (tarea != null && !todas.equals(tarea) && !tarea.equals(linea.tarea())) {
                return false;
            }
            return busqueda.isEmpty() || linea.texto().toLowerCase(Locale.ROOT).contains(busqueda);
        });
    }

    private void desplazarAlFinal() {
        if (!filtradas.isEmpty()) {
            listaLineas.scrollTo(filtradas.size() - 1);
        }
    }

    // ------------------------------------------------------------------ acciones

    @FXML
    private void copiar() {
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(contexto.consola().comoTexto());
        Clipboard.getSystemClipboard().setContent(contenido);
    }

    @FXML
    private void guardar() {
        FileChooser selector = new FileChooser();
        selector.setTitle(Textos.t("consola.guardar_la_consola"));
        selector.setInitialFileName("consola-" + LocalDate.now() + ".txt");
        selector.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Texto", "*.txt"));
        java.io.File destino = selector.showSaveDialog(listaLineas.getScene().getWindow());
        if (destino == null) {
            return;
        }
        try {
            contexto.consola().guardarEn(Path.of(destino.getAbsolutePath()));
        } catch (IOException e) {
            log.warn("No se ha podido guardar la consola", e);
            Dialogos.error(listaLineas.getScene().getWindow(), "Guardar la consola",
                    new ErrorAmistoso(Textos.t("consola.no_se_ha_podido_guardar"),
                            Textos.t("consola.comprueba_permisos"), e.toString()));
        }
    }

    @FXML
    private void limpiar() {
        contexto.consola().limpiar();
    }

    @FXML
    private void cerrar() {
        alCerrar.run();
    }

    /** Hora en gris, tarea en texto secundario y el mensaje con el color de su nivel. */
    private static final class CeldaLinea extends ListCell<LineaConsola> {

        private final Label hora = new Label();
        private final Label tarea = new Label();
        private final Label texto = new Label();
        private final HBox caja = new HBox(10, hora, tarea, texto);

        CeldaLinea() {
            hora.getStyleClass().add("consola-hora");
            tarea.getStyleClass().addAll("texto-consola", "texto-3");
            tarea.setMinWidth(104);
            texto.getStyleClass().add("texto-consola");
            HBox.setHgrow(texto, Priority.ALWAYS);
            caja.setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("celda-consola");
        }

        @Override
        protected void updateItem(LineaConsola linea, boolean vacio) {
            super.updateItem(linea, vacio);
            texto.getStyleClass().removeAll("consola-info", "consola-aviso", "consola-error");
            if (vacio || linea == null) {
                setGraphic(null);
                return;
            }
            hora.setText(linea.horaTexto());
            tarea.setText(linea.tarea());
            texto.setText(linea.texto());
            texto.getStyleClass().add(linea.nivel().claseEstilo());
            setGraphic(caja);
        }
    }
}
