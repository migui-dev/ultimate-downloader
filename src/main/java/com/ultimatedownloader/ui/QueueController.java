package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.model.DownloadPhase;
import com.ultimatedownloader.model.JobStatus;
import com.ultimatedownloader.model.Tarea;
import com.ultimatedownloader.model.TipoTarea;
import com.ultimatedownloader.ui.iconos.Iconos;
import com.ultimatedownloader.ui.iconos.Lucide;
import com.ultimatedownloader.util.Formatos;
import com.ultimatedownloader.util.Textos;
import java.nio.file.Path;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Módulo Cola: una fila por tarea, sea descarga, conversión o separación. Las columnas se
 * enlazan a las propiedades de Tarea, así que se refrescan solas sin sondear nada.
 */
public final class QueueController {

    /**
     * Opción «todos» del filtro. De instancia y no constante: una constante se quedaría con el
     * idioma que hubiera al cargar la clase.
     */
    private final String todos = Textos.t("cola.todos_los_tipos");

    private final Contexto contexto;
    private final StringBinding tituloPestana;
    private FilteredList<Tarea> filtradas;

    @FXML private Label etiquetaResumenCola;
    @FXML private ComboBox<String> filtroTipo;
    @FXML private Spinner<Integer> selectorSimultaneas;
    @FXML private Button botonCancelarTodo;
    @FXML private Button botonLimpiar;
    @FXML private TableView<Tarea> tablaCola;
    @FXML private TableColumn<Tarea, String> colTitulo;
    @FXML private TableColumn<Tarea, String> colTipo;
    @FXML private TableColumn<Tarea, String> colEstado;
    @FXML private TableColumn<Tarea, Tarea> colProgreso;
    @FXML private TableColumn<Tarea, String> colVelocidad;
    @FXML private TableColumn<Tarea, String> colRestante;
    @FXML private TableColumn<Tarea, String> colDestino;
    @FXML private TableColumn<Tarea, Tarea> colAcciones;

    public QueueController(Contexto contexto) {
        this.contexto = contexto;
        this.tituloPestana = Bindings.createStringBinding(() -> {
            long activas = contexto.cola().tareas().stream()
                    .filter(t -> !t.estado().terminada())
                    .count();
            return activas > 0 ? Textos.t("cola.titulo_con_activas", activas) : Textos.t("shell.cola");
        }, contexto.cola().tareas());
    }

    @FXML
    private void initialize() {
        prepararTabla();
        prepararFiltro();
        prepararBarra();
    }

    private void prepararTabla() {
        filtradas = new FilteredList<>(contexto.cola().tareas(), tarea -> true);
        tablaCola.setItems(filtradas);
        tablaCola.setPlaceholder(new Label(Textos.t("cola.vacia")));
        tablaCola.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        colTitulo.setCellValueFactory(d -> d.getValue().tituloProperty());
        colTipo.setCellValueFactory(d ->
                new ReadOnlyObjectWrapper<>(d.getValue().tipo().nombreLegible()));
        colEstado.setCellValueFactory(d -> textoDeEstado(d.getValue()));
        colVelocidad.setCellValueFactory(d -> Bindings.createStringBinding(
                () -> Formatos.velocidad(d.getValue().velocidadBytesPorSegProperty().get()),
                d.getValue().velocidadBytesPorSegProperty()));
        colRestante.setCellValueFactory(d -> Bindings.createStringBinding(
                () -> Formatos.duracionCorta(d.getValue().segundosRestantesProperty().get()),
                d.getValue().segundosRestantesProperty()));
        colDestino.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(
                d.getValue().carpetaDestino().toString()));
        colProgreso.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        colAcciones.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));

        colEstado.setCellFactory(c -> new CeldaEstado());
        colProgreso.setCellFactory(c -> new CeldaProgreso());
        colAcciones.setCellFactory(c -> new CeldaAcciones());
        colVelocidad.setCellFactory(c -> new CeldaDato());
        colRestante.setCellFactory(c -> new CeldaDato());

        // Las filas van en el orden en que se encolaron: reordenar la tabla no tendría sentido.
        for (TableColumn<Tarea, ?> columna : tablaCola.getColumns()) {
            columna.setReorderable(false);
            columna.setSortable(false);
        }
        colVelocidad.getStyleClass().add("cabecera-dato");
        colRestante.getStyleClass().add("cabecera-dato");
    }

    private void prepararFiltro() {
        filtroTipo.getItems().add(todos);
        for (TipoTarea tipo : TipoTarea.values()) {
            filtroTipo.getItems().add(tipo.nombreLegible());
        }
        filtroTipo.setValue(todos);
        filtroTipo.valueProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
    }

    private void prepararBarra() {
        selectorSimultaneas.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, 8, contexto.ajustes().getDescargasSimultaneas()));
        selectorSimultaneas.valueProperty().addListener((obs, antes, cuantas) -> {
            contexto.cola().ajustarSimultaneas(cuantas);
            contexto.ajustes().setDescargasSimultaneas(cuantas);
            contexto.servicioAjustes().guardar();
        });

        botonCancelarTodo.setGraphic(Iconos.de(Lucide.CANCELAR));
        botonLimpiar.setGraphic(Iconos.de(Lucide.PAPELERA));

        etiquetaResumenCola.textProperty().bind(Bindings.createStringBinding(
                this::resumen, contexto.cola().tareas()));
    }

    private void aplicarFiltro() {
        String elegido = filtroTipo.getValue();
        filtradas.setPredicate(tarea -> elegido == null || todos.equals(elegido)
                || tarea.tipo().nombreLegible().equals(elegido));
    }

    private String resumen() {
        var tareas = contexto.cola().tareas();
        if (tareas.isEmpty()) {
            return "";
        }
        long completadas = tareas.stream().filter(t -> t.estado() == JobStatus.COMPLETADA).count();
        long conError = tareas.stream().filter(t -> t.estado() == JobStatus.ERROR).count();
        long activas = tareas.stream().filter(t -> !t.estado().terminada()).count();

        // El singular y el plural los decide el propio texto con un «choice», porque no todos
        // los idiomas los forman igual ni los parten por el mismo número.
        StringBuilder sb = new StringBuilder(Textos.t("cola.resumen_tareas", tareas.size()));
        if (activas > 0) {
            sb.append(" · ").append(Textos.t("cola.resumen_en_marcha", activas));
        }
        if (completadas > 0) {
            sb.append(" · ").append(Textos.t("cola.resumen_completadas", completadas));
        }
        if (conError > 0) {
            sb.append(" · ").append(Textos.t("cola.resumen_con_error", conError));
        }
        return sb.toString();
    }

    /** Estado, fase y, cuando lo hay, el dispositivo donde se está ejecutando. */
    private ObservableValue<String> textoDeEstado(Tarea tarea) {
        return Bindings.createStringBinding(() -> {
            JobStatus estado = tarea.estadoProperty().get();
            DownloadPhase fase = tarea.faseProperty().get();
            String dispositivo = tarea.dispositivoProperty().get();
            if (estado != JobStatus.EN_CURSO) {
                return estado.nombreLegible();
            }
            String texto = fase != null ? fase.nombreLegible() : estado.nombreLegible();
            return dispositivo != null && !dispositivo.isBlank()
                    ? texto + " · " + dispositivo
                    : texto;
        }, tarea.estadoProperty(), tarea.faseProperty(), tarea.dispositivoProperty());
    }

    public StringBinding tituloPestana() {
        return tituloPestana;
    }

    // ------------------------------------------------------------------ acciones

    @FXML
    private void cancelarTodo() {
        contexto.cola().tareas().stream()
                .filter(t -> t.estado().sePuedeCancelar())
                .toList()
                .forEach(contexto.cola()::cancelar);
    }

    @FXML
    private void limpiarTerminadas() {
        contexto.cola().limpiarTerminadas();
    }

    // ------------------------------------------------------------------ celdas

    /** Celda numérica: monoespaciada y alineada a la derecha. */
    private static final class CeldaDato extends TableCell<Tarea, String> {

        CeldaDato() {
            getStyleClass().add("columna-dato");
        }

        @Override
        protected void updateItem(String texto, boolean vacio) {
            super.updateItem(texto, vacio);
            setText(vacio ? null : texto);
        }
    }

    /** Estado con el mensaje de error como información emergente. */
    private static final class CeldaEstado extends TableCell<Tarea, String> {

        @Override
        protected void updateItem(String texto, boolean vacio) {
            super.updateItem(texto, vacio);
            getStyleClass().removeAll("estado-error", "estado-completada");
            if (vacio || texto == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(texto);
            Tarea tarea = getTableRow() != null ? getTableRow().getItem() : null;
            if (tarea == null) {
                return;
            }
            if (tarea.estado() == JobStatus.ERROR) {
                getStyleClass().add("estado-error");
                String mensaje = tarea.mensajeProperty().get();
                String sugerencia = tarea.sugerenciaProperty().get();
                setTooltip(new Tooltip(sugerencia == null ? mensaje : mensaje + "\n" + sugerencia));
            } else if (tarea.estado() == JobStatus.COMPLETADA) {
                getStyleClass().add("estado-completada");
                setTooltip(null);
            } else {
                setTooltip(null);
            }
        }
    }

    /** Barra de progreso con el porcentaje al lado y los bytes debajo. */
    private static final class CeldaProgreso extends TableCell<Tarea, Tarea> {

        private final ProgressBar barra = new ProgressBar(0);
        private final Label porcentaje = new Label();
        private final Label bytes = new Label();
        private final VBox caja = new VBox(4);
        private Tarea actual;

        CeldaProgreso() {
            barra.setMaxWidth(Double.MAX_VALUE);
            // El porcentaje no se encoge: si la columna va justa, lo que cede es la barra,
            // porque un «62...» recortado no dice nada y una barra más corta sí.
            porcentaje.setMinWidth(Region.USE_PREF_SIZE);
            porcentaje.getStyleClass().add("dato-apagado");
            bytes.getStyleClass().add("dato-apagado");
            HBox fila = new HBox(8, barra, porcentaje);
            HBox.setHgrow(barra, Priority.ALWAYS);
            fila.setAlignment(Pos.CENTER_LEFT);
            caja.getChildren().addAll(fila, bytes);
            caja.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Tarea tarea, boolean vacio) {
            super.updateItem(tarea, vacio);
            desenlazar();
            if (vacio || tarea == null) {
                setGraphic(null);
                return;
            }
            actual = tarea;
            barra.progressProperty().bind(tarea.progresoProperty());
            porcentaje.textProperty().bind(Bindings.createStringBinding(
                    () -> Formatos.porcentaje(tarea.progresoProperty().get()),
                    tarea.progresoProperty()));
            bytes.textProperty().bind(Bindings.createStringBinding(
                    () -> {
                        long hechos = tarea.bytesDescargadosProperty().get();
                        long total = tarea.bytesTotalesProperty().get();
                        if (total <= 0) {
                            return hechos > 0 ? Formatos.tamano(hechos) : "";
                        }
                        return Textos.t("cola.bytes_de", Formatos.tamano(hechos),
                                Formatos.tamano(total));
                    },
                    tarea.bytesDescargadosProperty(), tarea.bytesTotalesProperty()));
            setGraphic(caja);
        }

        private void desenlazar() {
            if (actual != null) {
                barra.progressProperty().unbind();
                porcentaje.textProperty().unbind();
                bytes.textProperty().unbind();
                actual = null;
            }
        }
    }

    /** Botones de cancelar, reintentar y abrir carpeta, según el estado. */
    private final class CeldaAcciones extends TableCell<Tarea, Tarea> {

        private final Button cancelar = new Button();
        private final Button reintentar = new Button();
        private final Button abrir = new Button();
        private final HBox caja = new HBox(4, cancelar, reintentar, abrir);

        CeldaAcciones() {
            cancelar.setGraphic(Iconos.de(Lucide.CERRAR));
            cancelar.setTooltip(new Tooltip(Textos.t("comun.cancelar")));
            cancelar.getStyleClass().add("boton-icono");
            reintentar.setGraphic(Iconos.de(Lucide.REINTENTAR));
            reintentar.setTooltip(new Tooltip(Textos.t("cola.reintentar")));
            reintentar.getStyleClass().add("boton-icono");
            abrir.setGraphic(Iconos.de(Lucide.CARPETA));
            abrir.setTooltip(new Tooltip(Textos.t("comun.abrir_la_carpeta")));
            abrir.getStyleClass().add("boton-icono");
            caja.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(Tarea tarea, boolean vacio) {
            super.updateItem(tarea, vacio);
            if (vacio || tarea == null) {
                setGraphic(null);
                return;
            }
            cancelar.setOnAction(e -> contexto.cola().cancelar(tarea));
            reintentar.setOnAction(e -> contexto.cola().reintentar(tarea));
            abrir.setOnAction(e -> {
                Path archivo = tarea.archivoFinal();
                Dialogos.abrirEnExplorador(tarea.carpetaDestino(), archivo);
            });

            cancelar.disableProperty().bind(Bindings.createBooleanBinding(
                    () -> !tarea.estadoProperty().get().sePuedeCancelar(), tarea.estadoProperty()));
            reintentar.disableProperty().bind(Bindings.createBooleanBinding(
                    () -> !tarea.estadoProperty().get().sePuedeReintentar(), tarea.estadoProperty()));
            setGraphic(caja);
        }
    }
}
