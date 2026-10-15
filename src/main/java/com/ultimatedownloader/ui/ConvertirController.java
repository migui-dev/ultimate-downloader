package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.calidad.FormatoSalida;
import com.ultimatedownloader.model.calidad.NivelCalidad;
import com.ultimatedownloader.model.convert.ConversionRequest;
import com.ultimatedownloader.model.convert.MediaInfo;
import com.ultimatedownloader.model.convert.PistaMedia;
import com.ultimatedownloader.model.convert.TipoPista;
import com.ultimatedownloader.service.convert.RemuxDecider;
import com.ultimatedownloader.ui.iconos.Iconos;
import com.ultimatedownloader.ui.iconos.Lucide;
import com.ultimatedownloader.util.Formatos;
import com.ultimatedownloader.util.Textos;
import com.ultimatedownloader.viewmodel.ConvertirViewModel;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Módulo Convertir: pasa archivos de un formato a otro con ffmpeg. Se pueden soltar encima o
 * elegirlos con el selector, y van por lotes a la misma cola que las descargas.
 */
public final class ConvertirController {

    private static final Logger log = LoggerFactory.getLogger(ConvertirController.class);

    private final Contexto contexto;
    private final ConvertirViewModel vm;

    private Runnable alIrALaCola = () -> { };

    @FXML private Label etiquetaCuenta;
    @FXML private Button botonAnadir;
    @FXML private Button botonQuitar;
    @FXML private Button botonLimpiar;
    @FXML private VBox zonaSoltar;
    @FXML private TableView<MediaInfo> tablaArchivos;
    @FXML private TableColumn<MediaInfo, String> colNombre;
    @FXML private TableColumn<MediaInfo, String> colTipo;
    @FXML private TableColumn<MediaInfo, String> colDuracion;
    @FXML private TableColumn<MediaInfo, String> colTamano;
    @FXML private Label etiquetaError;

    @FXML private VBox ficha;
    @FXML private Label fichaNombre;
    @FXML private Label fichaResumen;
    @FXML private VBox fichaPistas;

    @FXML private ToggleGroup grupoCalidad;
    @FXML private ToggleButton calidadMaxima;
    @FXML private ToggleButton calidadMedia;
    @FXML private ToggleButton calidadMinima;
    @FXML private ComboBox<FormatoSalida> comboFormato;
    @FXML private Label etiquetaPerfil;
    @FXML private CheckBox casillaRemux;
    @FXML private CheckBox casillaSoloAudio;
    @FXML private CheckBox casillaGpu;
    @FXML private Label etiquetaRemux;
    @FXML private VBox cajaAvisos;

    @FXML private TitledPane panelAvanzado;
    @FXML private Label etiquetaAvanzadoLote;
    @FXML private TextField campoAncho;
    @FXML private TextField campoAlto;
    @FXML private TextField campoFps;
    @FXML private TextField campoFrecuencia;
    @FXML private TextField campoCanales;
    @FXML private ComboBox<PistaMedia> comboPistaAudio;
    @FXML private TextField campoInicio;
    @FXML private TextField campoFin;

    @FXML private CarpetaDestinoController carpetaDestinoController;

    @FXML private Label etiquetaResumen;
    @FXML private Button botonConvertir;

    public ConvertirController(Contexto contexto) {
        this.contexto = contexto;
        this.vm = new ConvertirViewModel(contexto);
    }

    @FXML
    private void initialize() {
        prepararIconos();
        prepararTabla();
        prepararSoltar();
        prepararSalida();
        prepararAvanzado();
        prepararCarpeta();

        vm.errorProperty().addListener((obs, antes, ahora) -> {
            boolean hay = ahora != null;
            mostrar(etiquetaError, hay);
            etiquetaError.setText(hay ? ahora.textoCompleto() : "");
        });
        vm.archivos().addListener((ListChangeListener<MediaInfo>) c -> refrescar());
        vm.seleccionadoProperty().addListener((obs, antes, ahora) -> refrescar());

        refrescar();
    }

    /** Qué hacer cuando una conversión se encola; lo fija el armazón. */
    public void alIrALaCola(Runnable accion) {
        this.alIrALaCola = accion != null ? accion : () -> { };
    }

    // ------------------------------------------------------------------ preparación

    private void prepararIconos() {
        botonAnadir.setGraphic(Iconos.de(Lucide.ANADIR));
        botonQuitar.setGraphic(Iconos.de(Lucide.CERRAR));
        botonLimpiar.setGraphic(Iconos.de(Lucide.PAPELERA));
        botonConvertir.setGraphic(Iconos.de(Lucide.CONVERTIR));
    }

    private void prepararTabla() {
        tablaArchivos.setItems(vm.archivos());
        // La tabla crece con los archivos y se para a las seis filas: con un solo archivo no
        // deja un hueco vacío enorme, y con muchos no empuja el resto fuera de la pantalla.
        tablaArchivos.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> 38.0 + 36 * Math.min(6, Math.max(2, vm.archivos().size())),
                vm.archivos()));
        tablaArchivos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaArchivos.setPlaceholder(new Label(Textos.t("convertir.arrastra_aqui")));

        columna(colNombre, MediaInfo::nombreArchivo, false);
        columna(colTipo, this::descripcionDe, false);
        columna(colDuracion, MediaInfo::duracionTexto, true);
        columna(colTamano, m -> Formatos.tamano(m.tamanoBytes()), true);

        tablaArchivos.getSelectionModel().selectedItemProperty().addListener(
                (obs, antes, ahora) -> {
                    if (ahora != null) {
                        vm.seleccionadoProperty().set(ahora);
                    }
                });
        vm.seleccionadoProperty().addListener((obs, antes, ahora) -> {
            if (ahora != null && tablaArchivos.getSelectionModel().getSelectedItem() != ahora) {
                tablaArchivos.getSelectionModel().select(ahora);
            }
        });
    }

    private String descripcionDe(MediaInfo info) {
        if (info.esSoloAudio()) {
            return Textos.t("convertir.solo_audio_prefijo") + " " + info.primeraDeAudio()
                    .map(p -> RemuxDecider.nombreLegible(p.codec())).orElse("?");
        }
        String video = info.primeraDeVideo()
                .map(p -> RemuxDecider.nombreLegible(p.codec()) + " " + p.resolucionTexto())
                .orElse(Textos.t("convertir.sin_imagen"));
        String audio = info.primeraDeAudio()
                .map(p -> RemuxDecider.nombreLegible(p.codec())).orElse(Textos.t("convertir.sin_audio"));
        return video + " + " + audio;
    }

    /** Arrastrar y soltar sobre la lista. */
    private void prepararSoltar() {
        zonaSoltar.setOnDragOver(evento -> {
            if (evento.getDragboard().hasFiles()) {
                evento.acceptTransferModes(TransferMode.COPY);
                // dragOver se dispara continuamente: sin la comprobación la clase se
                // añadiría cientos de veces a la misma lista.
                if (!zonaSoltar.getStyleClass().contains("soltando")) {
                    zonaSoltar.getStyleClass().add("soltando");
                }
            }
            evento.consume();
        });
        zonaSoltar.setOnDragExited(evento -> {
            zonaSoltar.getStyleClass().remove("soltando");
            evento.consume();
        });
        zonaSoltar.setOnDragDropped(this::soltar);
    }

    private void soltar(DragEvent evento) {
        zonaSoltar.getStyleClass().remove("soltando");
        if (!evento.getDragboard().hasFiles()) {
            evento.setDropCompleted(false);
            evento.consume();
            return;
        }
        List<Path> rutas = evento.getDragboard().getFiles().stream()
                .map(File::toPath)
                .filter(Files::isRegularFile)
                .toList();
        vm.anadir(rutas, this::refrescar);
        evento.setDropCompleted(true);
        evento.consume();
    }

    private void prepararSalida() {
        comboFormato.getItems().setAll(FormatoSalida.values());
        comboFormato.setValue(FormatoSalida.MP4);
        comboFormato.valueProperty().bindBidirectional(vm.formatoProperty());
        comboFormato.setConverter(Controles.comoTexto(formato -> formato == null ? ""
                : formato.nombreLegible() + "  ·  "
                        + Textos.t(formato.esAudio() ? "comun.audio" : "comun.video")));

        calidadMaxima.setUserData(NivelCalidad.MAXIMA);
        calidadMedia.setUserData(NivelCalidad.MEDIA);
        calidadMinima.setUserData(NivelCalidad.MINIMA);
        grupoCalidad.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoCalidad.selectToggle(antes);
                return;
            }
            vm.nivelProperty().set((NivelCalidad) ahora.getUserData());
            refrescar();
        });

        casillaRemux.selectedProperty().bindBidirectional(vm.remuxearProperty());
        casillaSoloAudio.selectedProperty().bindBidirectional(vm.soloAudioProperty());
        casillaGpu.selectedProperty().bindBidirectional(vm.usarGpuProperty());
        casillaGpu.setTooltip(new Tooltip(Textos.t("convertir.gpu_mas_rapido")));

        vm.formatoProperty().addListener((obs, antes, ahora) -> refrescar());
        vm.remuxearProperty().addListener((obs, antes, ahora) -> refrescar());
        vm.soloAudioProperty().addListener((obs, antes, ahora) -> refrescar());
        vm.usarGpuProperty().addListener((obs, antes, ahora) -> refrescar());
    }

    private void prepararAvanzado() {
        campoAncho.textProperty().bindBidirectional(vm.anchoProperty());
        campoAlto.textProperty().bindBidirectional(vm.altoProperty());
        campoFps.textProperty().bindBidirectional(vm.fpsProperty());
        campoFrecuencia.textProperty().bindBidirectional(vm.frecuenciaProperty());
        campoCanales.textProperty().bindBidirectional(vm.canalesProperty());
        campoInicio.textProperty().bindBidirectional(vm.inicioProperty());
        campoFin.textProperty().bindBidirectional(vm.finProperty());

        for (TextField campo : List.of(campoAncho, campoAlto, campoFps, campoFrecuencia,
                campoCanales, campoInicio, campoFin)) {
            campo.textProperty().addListener((obs, antes, ahora) -> refrescarAvisos());
        }

        comboPistaAudio.valueProperty().bindBidirectional(vm.pistaAudioProperty());
        comboPistaAudio.setConverter(Controles.comoTexto(ConvertirController::nombreDePista));
    }

    private static String nombreDePista(PistaMedia pista) {
        if (pista == null) {
            return "";
        }
        String idioma = pista.idiomaTexto();
        return Textos.t("comun.pista_numero", pista.indiceEnTipo() + 1)
                + " · " + RemuxDecider.nombreLegible(pista.codec())
                + (idioma != null ? " · " + idioma : "");
    }

    private void prepararCarpeta() {
        carpetaDestinoController.textos(Textos.t("comun.elige_la_carpeta_de_destino"),
                Textos.t("convertir.carpeta_no_existe"));
        carpetaDestinoController.estimacion(vm::tamanoEstimadoTotal);
        vm.destino(carpetaDestinoController::carpeta);
    }

    // ------------------------------------------------------------------ refrescos

    private void refrescar() {
        int cuantos = vm.archivos().size();
        etiquetaCuenta.setText(cuantos == 0 ? "" : Textos.t("comun.cuenta_archivos", cuantos));
        botonConvertir.setDisable(cuantos == 0);
        botonQuitar.setDisable(vm.seleccionadoProperty().get() == null);
        botonLimpiar.setDisable(cuantos == 0);

        MediaInfo info = vm.seleccionadoProperty().get();
        mostrar(ficha, info != null);
        if (info != null) {
            fichaNombre.setText(info.nombreArchivo());
            fichaResumen.setText(info.resumen());
            fichaPistas.getChildren().clear();
            for (PistaMedia pista : info.pistas()) {
                Label etiqueta = new Label(pista.resumen());
                etiqueta.getStyleClass().add("texto-3");
                fichaPistas.getChildren().add(etiqueta);
            }
            comboPistaAudio.getItems().setAll(info.pistasDe(TipoPista.AUDIO));
            if (comboPistaAudio.getValue() == null && !comboPistaAudio.getItems().isEmpty()) {
                comboPistaAudio.setValue(comboPistaAudio.getItems().get(0));
            }
        }

        RemuxDecider.Resultado remux = vm.puedeRemuxear();
        casillaRemux.setDisable(!remux.posible());
        etiquetaPerfil.setText(vm.perfil().resumen());
        etiquetaRemux.setText(textoDeRemux(remux));
        mostrar(etiquetaAvanzadoLote, cuantos > 1);
        panelAvanzado.setDisable(cuantos > 1);

        refrescarAvisos();
        carpetaDestinoController.refrescarEspacio();
        refrescarResumen();
    }

    private String textoDeRemux(RemuxDecider.Resultado remux) {
        if (vm.archivos().isEmpty()) {
            return "";
        }
        if (vm.remuxearProperty().get() && remux.posible()) {
            return Textos.t("convertir.se_copian_las_pistas");
        }
        if (!remux.posible()) {
            return remux.motivo() + " " + Textos.t("convertir.se_recodificara");
        }
        return Textos.t("convertir.se_recodifica");
    }

    private void refrescarAvisos() {
        List<String> avisos = vm.avisosDeCalidad();
        mostrar(cajaAvisos, !avisos.isEmpty());
        cajaAvisos.getChildren().clear();
        for (String aviso : avisos) {
            Label etiqueta = new Label(aviso);
            etiqueta.setWrapText(true);
            etiqueta.getStyleClass().add("texto-2");
            cajaAvisos.getChildren().add(etiqueta);
        }
        refrescarResumen();
    }

    private void refrescarResumen() {
        if (vm.archivos().isEmpty()) {
            etiquetaResumen.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Textos.t("comun.cuenta_archivos", vm.archivos().size()));
        sb.append(" → ").append(vm.formatoProperty().get().nombreLegible());
        if (!vm.remuxearProperty().get() || !vm.puedeRemuxear().posible()) {
            sb.append(" · ").append(vm.nivelProperty().get().nombreLegible());
        }
        long estimado = vm.tamanoEstimadoTotal();
        if (estimado > 0) {
            sb.append("   ").append(Formatos.tamano(estimado, true));
        }
        etiquetaResumen.setText(sb.toString());
    }

    // ------------------------------------------------------------------ acciones

    @FXML
    private void anadir() {
        FileChooser selector = new FileChooser();
        selector.setTitle(Textos.t("convertir.elige_los_archivos"));
        selector.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(Textos.t("comun.audio_y_video"),
                        "*.mp4", "*.mkv", "*.mov", "*.webm", "*.avi", "*.m4v", "*.flv", "*.wmv",
                        "*.ts", "*.mpg", "*.mpeg",
                        "*.mp3", "*.m4a", "*.aac", "*.wav", "*.flac", "*.opus", "*.ogg",
                        "*.aiff", "*.wma", "*.alac"),
                new FileChooser.ExtensionFilter(Textos.t("comun.todos_los_archivos"), "*.*"));
        List<File> elegidos = selector.showOpenMultipleDialog(ventana());
        if (elegidos == null || elegidos.isEmpty()) {
            return;
        }
        vm.anadir(elegidos.stream().map(File::toPath).toList(), this::refrescar);
    }

    @FXML
    private void quitar() {
        MediaInfo elegido = tablaArchivos.getSelectionModel().getSelectedItem();
        if (elegido != null) {
            vm.quitar(elegido);
        }
    }

    @FXML
    private void limpiar() {
        vm.limpiar();
    }

    @FXML
    private void convertir() {
        if (contexto.rutaFfmpeg().isEmpty()) {
            Dialogos.error(ventana(), Textos.t("comun.falta_ffmpeg"), new ErrorAmistoso(
                    Textos.t("comun.no_se_encuentra_ffmpeg"),
                    Textos.t("descargar.abrelo_desde_ajustes")));
            return;
        }
        List<ConversionRequest> peticiones = vm.construirPeticiones();
        if (peticiones.isEmpty()) {
            return;
        }
        for (ConversionRequest peticion : peticiones) {
            contexto.encolarConversion(peticion);
        }
        contexto.recordarCarpeta(carpetaDestinoController.carpeta());
        alIrALaCola.run();
    }

    /** Recoge lo que se haya cambiado en los ajustes. */
    public void refrescarTrasAjustes() {
        carpetaDestinoController.usar(contexto.carpetaDestino());
    }

    // ------------------------------------------------------------------ utilidades

    private static void columna(TableColumn<MediaInfo, String> columna,
                                Function<MediaInfo, String> texto,
                                boolean numerica) {
        columna.setCellValueFactory(datos -> valor(texto.apply(datos.getValue())));
        columna.setReorderable(false);
        columna.setSortable(false);
        if (numerica) {
            columna.getStyleClass().add("cabecera-dato");
            columna.setCellFactory(c -> new TableCell<>() {
                {
                    getStyleClass().add("columna-dato");
                }

                @Override
                protected void updateItem(String texto, boolean vacio) {
                    super.updateItem(texto, vacio);
                    setText(vacio ? null : texto);
                }
            });
        }
    }

    private static ObservableValue<String> valor(String texto) {
        return new SimpleObjectProperty<>(texto);
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private Window ventana() {
        return tablaArchivos.getScene() != null ? tablaArchivos.getScene().getWindow() : null;
    }

    public ConvertirViewModel modelo() {
        return vm;
    }

    /** Añade archivos desde fuera; lo usan las opciones de desarrollo. */
    public void anadirArchivos(List<Path> rutas) {
        vm.anadir(new ArrayList<>(rutas), this::refrescar);
    }
}
