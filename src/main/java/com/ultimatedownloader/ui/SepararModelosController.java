package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.fx.EnSegundoPlano;
import com.ultimatedownloader.model.separacion.Arquitectura;
import com.ultimatedownloader.model.separacion.ModeloUvr;
import com.ultimatedownloader.model.separacion.SeparacionRequest.ModoVariosModelos;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.util.Formatos;
import com.ultimatedownloader.util.Textos;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catálogo de modelos de UVR: buscar, filtrar, marcar favoritos, descargar y borrar. Son 169 y
 * los lista el propio motor, así que se piden una vez y se guardan en memoria.
 */
public final class SepararModelosController {

    private static final Logger log = LoggerFactory.getLogger(SepararModelosController.class);

    /** Algoritmos de mezcla que admite el motor. El nombre que se lee sale del idioma elegido. */
    private static final List<String> CLAVES_ALGORITMOS = List.of(
            "avg_wave", "median_wave", "min_wave", "max_wave",
            "avg_fft", "median_fft", "min_fft", "max_fft",
            "uvr_max_spec", "uvr_min_spec", "ensemble_wav");

    private final Contexto contexto;

    private final ObservableList<ModeloUvr> catalogo = FXCollections.observableArrayList();
    private final FilteredList<ModeloUvr> filtrados = new FilteredList<>(catalogo);

    private Runnable alCambiar = () -> { };
    private Runnable alPedirConsola = () -> { };

    private boolean pedido;
    private Runnable trasCargar;

    @FXML private Label etiquetaCuentaModelos;
    @FXML private TextField campoBuscarModelo;
    @FXML private ComboBox<Arquitectura> comboArquitectura;
    @FXML private CheckBox casillaSoloDescargados;
    @FXML private CheckBox casillaSoloFavoritos;
    @FXML private TableView<ModeloUvr> tablaModelos;
    @FXML private TableColumn<ModeloUvr, ModeloUvr> colFavorito;
    @FXML private TableColumn<ModeloUvr, String> colModelo;
    @FXML private TableColumn<ModeloUvr, String> colArquitectura;
    @FXML private TableColumn<ModeloUvr, String> colPistas;
    @FXML private TableColumn<ModeloUvr, String> colSdr;
    @FXML private TableColumn<ModeloUvr, String> colEstadoModelo;
    @FXML private Label etiquetaSeleccionModelos;
    @FXML private ProgressBar barraModelo;
    @FXML private Label etiquetaDescargaModelo;
    @FXML private Button botonDescargarModelo;
    @FXML private Button botonBorrarModelo;

    @FXML private VBox cajaVariosModelos;
    @FXML private ToggleGroup grupoVariosModelos;
    @FXML private ToggleButton variosPorSeparado;
    @FXML private ToggleButton variosMezcla;
    @FXML private HBox cajaAlgoritmo;
    @FXML private ComboBox<Algoritmo> comboAlgoritmo;
    @FXML private Label etiquetaVariosModelos;

    /** Un algoritmo de mezcla con su nombre en el idioma de la aplicación. */
    public record Algoritmo(String clave, String nombre) {

        @Override
        public String toString() {
            return nombre;
        }
    }

    public SepararModelosController(Contexto contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        prepararTabla();
        prepararFiltros();
        prepararMezcla();
        refrescar();
    }

    /** Qué hacer cuando cambia la selección o la forma de mezclar. */
    public void alCambiar(Runnable accion) {
        this.alCambiar = accion != null ? accion : () -> { };
    }

    /** Qué hacer cuando empieza una descarga, que es larga y se sigue por la consola. */
    public void alPedirConsola(Runnable accion) {
        this.alPedirConsola = accion != null ? accion : () -> { };
    }

    /** Pide el catálogo la primera vez; las siguientes no hace nada. */
    public void cargar() {
        if (pedido) {
            return;
        }
        pedido = true;
        EnSegundoPlano.calcular(contexto.tareas(), () -> contexto.modelos().modelos(),
                this::catalogoCargado,
                causa -> {
                    tablaModelos.setPlaceholder(
                            new Label(Textos.t("separar.no_se_ha_podido_leer_catalogo")));
                    pedido = false;
                    if (causa instanceof AppException fallo) {
                        log.warn("No se ha podido listar los modelos: {}", fallo.error().mensaje());
                    }
                });
    }

    /** Carga el catálogo y hace algo en cuanto esté; lo usa el arranque de desarrollo. */
    public void cargarYLuego(Runnable accion) {
        trasCargar = accion;
        cargar();
    }

    /** Tras borrar el motor el catálogo deja de valer: la próxima vez se vuelve a pedir. */
    public void olvidar() {
        pedido = false;
    }

    public List<ModeloUvr> seleccionados() {
        return List.copyOf(tablaModelos.getSelectionModel().getSelectedItems());
    }

    public ModoVariosModelos modo() {
        return variosMezcla.isSelected() && seleccionados().size() > 1
                ? ModoVariosModelos.MEZCLA
                : ModoVariosModelos.POR_SEPARADO;
    }

    public String algoritmo() {
        Algoritmo elegido = comboAlgoritmo.getValue();
        return elegido != null ? elegido.clave() : null;
    }

    /** Deja el panel como lo describe un ajuste guardado. */
    public void aplicar(List<String> ficheros, ModoVariosModelos modo, String algoritmo) {
        tablaModelos.getSelectionModel().clearSelection();
        for (String fichero : ficheros) {
            catalogo.stream()
                    .filter(modelo -> modelo.fichero().equals(fichero))
                    .findFirst()
                    .ifPresent(modelo -> tablaModelos.getSelectionModel().select(modelo));
        }
        grupoVariosModelos.selectToggle(
                modo == ModoVariosModelos.MEZCLA ? variosMezcla : variosPorSeparado);
        algoritmos().stream()
                .filter(a -> a.clave().equals(algoritmo))
                .findFirst()
                .ifPresent(comboAlgoritmo::setValue);
    }

    // ------------------------------------------------------------------ tabla y filtros

    private void prepararTabla() {
        tablaModelos.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tablaModelos.setPlaceholder(new Label(Textos.t("separar.cargando_catalogo")));

        colFavorito.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colFavorito.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(ModeloUvr modelo, boolean vacio) {
                super.updateItem(modelo, vacio);
                if (vacio || modelo == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                setText(esFavorito(modelo) ? "★" : "☆");
                setOnMouseClicked(e -> alternarFavorito(modelo));
            }
        });
        colFavorito.getStyleClass().add("columna-dato");

        colModelo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombreCorto()));
        colArquitectura.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().arquitectura().nombreLegible()));
        colPistas.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().pistasLegibles()));
        colSdr.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().sdr() > 0 ? Formatos.decimal(c.getValue().sdr(), 2) : "—"));
        colSdr.getStyleClass().add("columna-dato");
        colEstadoModelo.setCellValueFactory(c -> new SimpleStringProperty(estadoDe(c.getValue())));
        colEstadoModelo.getStyleClass().add("columna-dato");

        // El espacio de sobra se lo queda el nombre del modelo, que es lo que puede ser largo;
        // el resto de columnas se quedan como están gracias a su anchura máxima.
        tablaModelos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        colFavorito.setMaxWidth(40);
        colArquitectura.setMaxWidth(190);
        colPistas.setMaxWidth(220);
        colSdr.setMaxWidth(90);
        colEstadoModelo.setMaxWidth(130);

        SortedList<ModeloUvr> ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tablaModelos.comparatorProperty());
        tablaModelos.setItems(ordenados);

        tablaModelos.getSelectionModel().getSelectedItems()
                .addListener((ListChangeListener<ModeloUvr>) cambio -> cambiado());
    }

    private void prepararFiltros() {
        comboArquitectura.getItems().add(null);
        comboArquitectura.getItems().addAll(Arquitectura.values());
        comboArquitectura.setConverter(Controles.comoTexto(arquitectura -> arquitectura == null
                ? Textos.t("separar.todas_las_arquitecturas")
                : arquitectura.nombreLegible()));
        comboArquitectura.setValue(null);

        campoBuscarModelo.textProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        comboArquitectura.valueProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        casillaSoloDescargados.selectedProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        casillaSoloFavoritos.selectedProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
    }

    private void prepararMezcla() {
        comboAlgoritmo.getItems().setAll(algoritmos());
        comboAlgoritmo.setValue(comboAlgoritmo.getItems().get(0));
        comboAlgoritmo.valueProperty().addListener((obs, antes, ahora) -> cambiado());
        grupoVariosModelos.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoVariosModelos.selectToggle(antes);
                return;
            }
            cambiado();
        });
    }

    private void aplicarFiltro() {
        String busqueda = campoBuscarModelo.getText() == null
                ? "" : campoBuscarModelo.getText().strip().toLowerCase(Locale.ROOT);
        Arquitectura arquitectura = comboArquitectura.getValue();
        boolean soloDescargados = casillaSoloDescargados.isSelected();
        boolean soloFavoritos = casillaSoloFavoritos.isSelected();

        filtrados.setPredicate(modelo -> {
            if (arquitectura != null && modelo.arquitectura() != arquitectura) {
                return false;
            }
            if (soloDescargados && !contexto.modelos().descargado(modelo)) {
                return false;
            }
            if (soloFavoritos && !esFavorito(modelo)) {
                return false;
            }
            if (busqueda.isEmpty()) {
                return true;
            }
            return modelo.nombre().toLowerCase(Locale.ROOT).contains(busqueda)
                    || modelo.fichero().toLowerCase(Locale.ROOT).contains(busqueda)
                    || modelo.pistasLegibles().toLowerCase(Locale.ROOT).contains(busqueda);
        });
        refrescarCuenta();
    }

    private void catalogoCargado(List<ModeloUvr> modelos) {
        List<ModeloUvr> ordenados = new ArrayList<>(modelos);
        ordenados.sort(contexto.modelos().ordenHabitual());
        catalogo.setAll(ordenados);
        tablaModelos.setPlaceholder(new Label(Textos.t("separar.ningun_modelo_encaja")));
        aplicarFiltro();
        seleccionarRecomendado();
        if (trasCargar != null) {
            Runnable accion = trasCargar;
            trasCargar = null;
            Platform.runLater(accion);
        }
    }

    /** Deja elegido el primer modelo que separe voz e instrumental, que es lo habitual. */
    private void seleccionarRecomendado() {
        if (!tablaModelos.getSelectionModel().isEmpty() || filtrados.isEmpty()) {
            return;
        }
        filtrados.stream()
                .filter(ModeloUvr::separaVozEInstrumental)
                .findFirst()
                .ifPresent(modelo -> tablaModelos.getSelectionModel().select(modelo));
    }

    private boolean esFavorito(ModeloUvr modelo) {
        return contexto.ajustes().getModelosFavoritos().contains(modelo.fichero());
    }

    private void alternarFavorito(ModeloUvr modelo) {
        List<String> favoritos = new ArrayList<>(contexto.ajustes().getModelosFavoritos());
        if (!favoritos.remove(modelo.fichero())) {
            favoritos.add(modelo.fichero());
        }
        contexto.ajustes().setModelosFavoritos(favoritos);
        contexto.servicioAjustes().guardar();
        tablaModelos.refresh();
        aplicarFiltro();
    }

    // ------------------------------------------------------------------ descargar y borrar

    @FXML
    private void descargarModelo() {
        List<ModeloUvr> elegidos = seleccionados().stream()
                .filter(modelo -> !contexto.modelos().descargado(modelo))
                .toList();
        if (elegidos.isEmpty()) {
            return;
        }
        botonDescargarModelo.setDisable(true);
        alPedirConsola.run();
        mostrarDescarga(true, 0);
        EnSegundoPlano.hacer(contexto.tareas(),
                () -> {
                    for (int i = 0; i < elegidos.size(); i++) {
                        int indice = i;
                        contexto.modelos().descargar(elegidos.get(i), contexto.consola(),
                                fraccion -> Platform.runLater(() ->
                                        mostrarDescarga(true,
                                                (indice + fraccion) / elegidos.size())));
                    }
                },
                () -> {
                    botonDescargarModelo.setDisable(false);
                    mostrarDescarga(false, 0);
                    tablaModelos.refresh();
                    cambiado();
                },
                causa -> {
                    botonDescargarModelo.setDisable(false);
                    mostrarDescarga(false, 0);
                    if (causa instanceof AppException fallo) {
                        Dialogos.error(ventana(), Textos.t("separar.descargar_el_modelo"),
                                fallo.error());
                    }
                });
    }

    @FXML
    private void borrarModelo() {
        List<ModeloUvr> elegidos = seleccionados().stream()
                .filter(contexto.modelos()::descargado)
                .toList();
        if (elegidos.isEmpty()) {
            return;
        }
        boolean seguro = Dialogos.confirmar(ventana(), Textos.t("separar.borrar_del_disco"),
                elegidos.size() == 1
                        ? Textos.t("separar.borrar_modelo_uno", elegidos.get(0).nombreCorto())
                        : Textos.t("separar.borrar_modelo_varios", elegidos.size()),
                Textos.t("separar.borrar"), Textos.t("separar.dejarlos"));
        if (!seguro) {
            return;
        }
        try {
            for (ModeloUvr modelo : elegidos) {
                contexto.modelos().borrar(modelo);
            }
        } catch (AppException e) {
            Dialogos.error(ventana(), Textos.t("separar.borrar_del_disco"), e.error());
        }
        tablaModelos.refresh();
        cambiado();
    }

    /** La barra solo está mientras se descarga; el resto del tiempo no ocupa sitio. */
    private void mostrarDescarga(boolean descargando, double fraccion) {
        mostrar(barraModelo, descargando);
        mostrar(etiquetaDescargaModelo, descargando);
        barraModelo.setProgress(fraccion);
        etiquetaDescargaModelo.setText(descargando ? Formatos.porcentaje(fraccion) : "");
    }

    // ------------------------------------------------------------------ refrescos

    private void cambiado() {
        refrescar();
        alCambiar.run();
    }

    private void refrescar() {
        List<ModeloUvr> elegidos = seleccionados();
        refrescarCuenta();
        refrescarSeleccion(elegidos);
        refrescarMezcla(elegidos);
    }

    private void refrescarCuenta() {
        if (catalogo.isEmpty()) {
            etiquetaCuentaModelos.setText("");
            return;
        }
        long descargados = catalogo.stream().filter(contexto.modelos()::descargado).count();
        etiquetaCuentaModelos.setText(Textos.t("separar.cuenta_modelos",
                filtrados.size(), catalogo.size(), descargados));
    }

    private void refrescarSeleccion(List<ModeloUvr> elegidos) {
        boolean hayPorDescargar = elegidos.stream()
                .anyMatch(modelo -> !contexto.modelos().descargado(modelo));
        botonDescargarModelo.setDisable(!hayPorDescargar);
        botonBorrarModelo.setDisable(elegidos.stream().noneMatch(contexto.modelos()::descargado));

        if (elegidos.isEmpty()) {
            etiquetaSeleccionModelos.setText(Textos.t("separar.elige_un_modelo"));
            return;
        }
        if (elegidos.size() > 1) {
            etiquetaSeleccionModelos.setText(Textos.t("separar.modelos_elegidos", elegidos.size()));
            return;
        }
        ModeloUvr modelo = elegidos.get(0);
        String estado = Textos.t(contexto.modelos().descargado(modelo)
                ? "separar.ya_esta_descargado"
                : "separar.se_descargara");
        etiquetaSeleccionModelos.setText(Textos.t("separar.modelo_elegido",
                modelo.nombreCorto(), modelo.arquitectura().descripcion(), estado));
    }

    private void refrescarMezcla(List<ModeloUvr> elegidos) {
        boolean varios = elegidos.size() > 1;
        mostrar(cajaVariosModelos, varios);
        mostrar(cajaAlgoritmo, varios && variosMezcla.isSelected());

        if (!varios) {
            etiquetaVariosModelos.setText("");
            return;
        }
        etiquetaVariosModelos.setText(variosMezcla.isSelected()
                ? Textos.t("separar.varios_mezcla", elegidos.size(),
                        comboAlgoritmo.getValue().nombre().toLowerCase(Locale.ROOT),
                        elegidos.get(0).nombreCorto())
                : Textos.t("separar.varios_por_separado", elegidos.size()));
    }

    private String estadoDe(ModeloUvr modelo) {
        if (!contexto.modelos().descargado(modelo)) {
            return "—";
        }
        long bytes = contexto.modelos().tamanoEnDisco(modelo);
        return bytes > 0 ? Formatos.tamano(bytes) : Textos.t("separar.descargado");
    }

    private static List<Algoritmo> algoritmos() {
        return CLAVES_ALGORITMOS.stream()
                .map(clave -> new Algoritmo(clave, Textos.t("separar.algoritmo." + clave)))
                .toList();
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private Window ventana() {
        return tablaModelos.getScene() != null ? tablaModelos.getScene().getWindow() : null;
    }
}
