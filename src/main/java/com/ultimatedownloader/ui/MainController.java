package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.model.AudioFormat;
import com.ultimatedownloader.model.AudioOutputFormat;
import com.ultimatedownloader.model.CompatibilidadContenedor;
import com.ultimatedownloader.model.CookieBrowser;
import com.ultimatedownloader.model.DownloadMode;
import com.ultimatedownloader.model.DownloadRequest;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.EstimacionTamano;
import com.ultimatedownloader.model.PistaDescargable;
import com.ultimatedownloader.model.SubtitleTrack;
import com.ultimatedownloader.model.VideoContainer;
import com.ultimatedownloader.model.VideoFormat;
import com.ultimatedownloader.model.VideoInfo;
import com.ultimatedownloader.service.FilenameTemplate;
import com.ultimatedownloader.ui.iconos.Iconos;
import com.ultimatedownloader.ui.iconos.Lucide;
import com.ultimatedownloader.util.Formatos;
import com.ultimatedownloader.util.Textos;
import com.ultimatedownloader.viewmodel.MainViewModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Módulo Descargar: analizar un enlace, elegir pistas y formato, fijar la carpeta y mandar la
 * descarga a la cola. Solo enlaza la vista con MainViewModel; aquí no hay lógica de negocio.
 */
public final class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private static final String SIN_DATO = "—";

    private final Contexto contexto;
    private final MainViewModel vm;

    private Runnable alIrALaCola = () -> { };

    @FXML private TextField campoUrl;
    @FXML private Button botonAnalizar;
    @FXML private Label etiquetaError;
    @FXML private Label etiquetaAviso;

    @FXML private VBox estadoVacio;
    @FXML private VBox esqueleto;

    @FXML private HBox tarjetaInfo;
    @FXML private StackPane marcoMiniatura;
    @FXML private ImageView miniatura;
    @FXML private Label etiquetaTitulo;
    @FXML private Label etiquetaCanal;
    @FXML private Label etiquetaMetadatos;

    @FXML private VBox tarjetaFormatos;
    @FXML private ToggleGroup grupoModo;
    @FXML private ToggleButton modoVideoAudio;
    @FXML private ToggleButton modoSoloVideo;
    @FXML private ToggleButton modoSoloAudio;

    @FXML private VBox cajaVideo;
    @FXML private Label etiquetaCuentaVideo;
    @FXML private TableView<VideoFormat> tablaVideo;
    @FXML private TableColumn<VideoFormat, String> colCalidad;
    @FXML private TableColumn<VideoFormat, String> colCodecVideo;
    @FXML private TableColumn<VideoFormat, String> colHdr;
    @FXML private TableColumn<VideoFormat, String> colContenedorVideo;
    @FXML private TableColumn<VideoFormat, String> colResolucion;
    @FXML private TableColumn<VideoFormat, String> colFps;
    @FXML private TableColumn<VideoFormat, String> colBitrateVideo;
    @FXML private TableColumn<VideoFormat, String> colTamanoVideo;

    @FXML private VBox cajaAudio;
    @FXML private Label etiquetaCuentaAudio;
    @FXML private TableView<AudioFormat> tablaAudio;
    @FXML private TableColumn<AudioFormat, String> colCodecAudio;
    @FXML private TableColumn<AudioFormat, String> colIdioma;
    @FXML private TableColumn<AudioFormat, String> colContenedorAudio;
    @FXML private TableColumn<AudioFormat, String> colCanales;
    @FXML private TableColumn<AudioFormat, String> colBitrateAudio;
    @FXML private TableColumn<AudioFormat, String> colTamanoAudio;

    @FXML private Region lineaSalida;
    @FXML private VBox tarjetaSalida;
    @FXML private ComboBox<VideoContainer> comboContenedor;
    @FXML private Label etiquetaRemux;
    @FXML private Label etiquetaFormatoAudio;
    @FXML private ComboBox<AudioOutputFormat> comboFormatoAudio;
    @FXML private TextField campoPlantilla;
    @FXML private MenuButton menuMarcadores;
    @FXML private Label etiquetaNombrePrevisto;
    @FXML private HBox cajaAvisoCompat;
    @FXML private Label etiquetaCompat;
    @FXML private Button botonContenedorCompatible;
    @FXML private Button botonRecodificar;
    @FXML private CheckBox casillaRecodificar;

    @FXML private CarpetaDestinoController carpetaDestinoController;

    @FXML private TitledPane panelAvanzado;
    @FXML private CheckBox casillaSubtitulos;
    @FXML private ComboBox<SubtitleTrack> comboIdiomaSubtitulos;
    @FXML private CheckBox casillaSubtitulosAuto;
    @FXML private CheckBox casillaIncrustarSubtitulos;
    @FXML private CheckBox casillaMiniatura;
    @FXML private CheckBox casillaMetadatos;
    @FXML private CheckBox casillaListaCompleta;
    @FXML private ComboBox<CookieBrowser> comboCookies;

    @FXML private Label etiquetaResumen;
    @FXML private Button botonAnadirCola;
    @FXML private Button botonDescargar;

    public MainController(Contexto contexto) {
        this.contexto = contexto;
        this.vm = new MainViewModel(contexto);
    }

    @FXML
    private void initialize() {
        prepararIconos();
        prepararTablaVideo();
        prepararTablaAudio();
        prepararModos();
        prepararSalida();
        prepararCarpeta();
        prepararAvanzado();
        prepararMensajes();
        prepararBotones();
        refrescarResumen();
    }

    /** Qué hacer cuando una descarga se encola; lo fija el armazón. */
    public void alIrALaCola(Runnable accion) {
        this.alIrALaCola = accion != null ? accion : () -> { };
    }

    // ------------------------------------------------------------------ preparación

    private void prepararIconos() {
        botonAnalizar.setGraphic(Iconos.de(Lucide.BUSCAR));
        botonDescargar.setGraphic(Iconos.de(Lucide.DESCARGAR));
        botonAnadirCola.setGraphic(Iconos.de(Lucide.ANADIR));
        menuMarcadores.setGraphic(Iconos.de(Lucide.ANADIR));
    }

    private void prepararTablaVideo() {
        tablaVideo.setItems(vm.formatosVideo());
        tablaVideo.setPlaceholder(new Label(Textos.t("descargar.vacio_video")));
        tablaVideo.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        columna(colCalidad, VideoFormat::etiquetaCalidad, false,
                Textos.t("descargar.mejor_disponible"));
        columna(colCodecVideo, f -> f.codec().nombreLegible(), false, null);
        columna(colHdr, VideoFormat::rangoDinamicoTexto, false, null);
        columna(colContenedorVideo, VideoFormat::contenedorTexto, false, null);
        columna(colResolucion, VideoFormat::resolucionTexto, true, null);
        columna(colFps, f -> Formatos.fps(f.fps()), true, null);
        columna(colBitrateVideo, f -> Formatos.bitrate(f.bitrateKbps()), true, null);
        columna(colTamanoVideo, f -> Formatos.tamano(f.tamanoBytes(), !f.tamanoExacto()),
                true, null);

        sincronizar(tablaVideo, vm.videoElegidoProperty());
    }

    private void prepararTablaAudio() {
        tablaAudio.setItems(vm.formatosAudio());
        tablaAudio.setPlaceholder(new Label(Textos.t("descargar.vacio_audio")));
        tablaAudio.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        columna(colCodecAudio, f -> f.codec().nombreLegible(), false,
                Textos.t("descargar.mejor_disponible"));
        columna(colIdioma, f -> f.esDrc()
                ? f.idiomaTexto() + " · " + Textos.t("descargar.drc")
                : f.idiomaTexto(), false, null);
        columna(colContenedorAudio, AudioFormat::contenedorTexto, false, null);
        columna(colCanales, AudioFormat::canalesTexto, false, null);
        columna(colBitrateAudio, f -> Formatos.bitrate(f.bitrateKbps()), true, null);
        columna(colTamanoAudio, f -> Formatos.tamano(f.tamanoBytes(), !f.tamanoExacto()),
                true, null);

        sincronizar(tablaAudio, vm.audioElegidoProperty());
    }

    /** La tabla y el modelo se siguen el uno al otro sin entrar en bucle. */
    private <T> void sincronizar(TableView<T> tabla, ObjectProperty<T> elegido) {
        tabla.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> {
            if (ahora != null) {
                elegido.set(ahora);
                refrescarResumen();
            }
        });
        elegido.addListener((obs, antes, ahora) -> {
            if (ahora != null && tabla.getSelectionModel().getSelectedItem() != ahora) {
                tabla.getSelectionModel().select(ahora);
            }
        });
    }

    private void prepararModos() {
        modoVideoAudio.setUserData(DownloadMode.VIDEO_Y_AUDIO);
        modoSoloVideo.setUserData(DownloadMode.SOLO_VIDEO);
        modoSoloAudio.setUserData(DownloadMode.SOLO_AUDIO);

        grupoModo.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoModo.selectToggle(antes);
                return;
            }
            vm.modoProperty().set((DownloadMode) ahora.getUserData());
        });
        vm.modoProperty().addListener((obs, antes, ahora) -> {
            seleccionarModo(ahora);
            aplicarModoALaVista(ahora);
            refrescarResumen();
        });
        seleccionarModo(vm.modoProperty().get());
        aplicarModoALaVista(vm.modoProperty().get());
    }

    private void seleccionarModo(DownloadMode modo) {
        for (Toggle toggle : grupoModo.getToggles()) {
            if (toggle.getUserData() == modo) {
                grupoModo.selectToggle(toggle);
                return;
            }
        }
    }

    /** Enseña u oculta lo que no tiene sentido en cada modo. */
    private void aplicarModoALaVista(DownloadMode modo) {
        boolean soloAudio = modo == DownloadMode.SOLO_AUDIO;
        mostrar(cajaVideo, modo.incluyeVideo());
        mostrar(cajaAudio, modo.incluyeAudio());
        casillaRecodificar.setDisable(soloAudio);
        mostrar(comboFormatoAudio, soloAudio);
        mostrar(etiquetaFormatoAudio, soloAudio);
        mostrar(comboContenedor, !soloAudio);
        mostrar(etiquetaRemux, !soloAudio);
        mostrar(casillaRecodificar, !soloAudio);
    }

    private void prepararSalida() {
        comboContenedor.getItems().setAll(VideoContainer.values());
        comboContenedor.valueProperty().bindBidirectional(vm.contenedorProperty());
        comboFormatoAudio.getItems().setAll(AudioOutputFormat.values());
        comboFormatoAudio.valueProperty().bindBidirectional(vm.formatoAudioProperty());
        campoPlantilla.textProperty().bindBidirectional(vm.plantillaNombreProperty());
        casillaRecodificar.selectedProperty().bindBidirectional(vm.recodificarProperty());

        prepararMarcadores();

        vm.estimacion().addListener((obs, antes, ahora) -> refrescarTamano(ahora));
        vm.compatibilidad().addListener((obs, antes, ahora) -> refrescarCompatibilidad(ahora));
        vm.nombrePrevisto().addListener((obs, antes, ahora) -> refrescarNombre());
        vm.recodificarProperty().addListener((obs, antes, ahora) -> {
            refrescarNombre();
            refrescarCompatibilidad(vm.compatibilidad().get());
        });
        vm.formatoAudioProperty().addListener((obs, antes, ahora) -> {
            refrescarNombre();
            refrescarResumen();
        });
        vm.contenedorProperty().addListener((obs, antes, ahora) -> refrescarNombre());

        refrescarTamano(vm.estimacion().get());
        refrescarCompatibilidad(vm.compatibilidad().get());
        refrescarNombre();
    }

    private void prepararMarcadores() {
        Tooltip ayuda = new Tooltip(Textos.t("descargar.marcadores_disponibles") + " "
                + String.join(", ", FilenameTemplate.marcadores().keySet()));
        ayuda.setShowDuration(Duration.seconds(30));
        campoPlantilla.setTooltip(ayuda);

        FilenameTemplate.marcadores().forEach((marcador, descripcion) -> {
            MenuItem elemento = new MenuItem(marcador + "  ·  " + descripcion);
            elemento.setOnAction(e -> insertarMarcador(marcador));
            menuMarcadores.getItems().add(elemento);
        });
    }

    private void insertarMarcador(String marcador) {
        String texto = campoPlantilla.getText() == null ? "" : campoPlantilla.getText();
        int donde = Math.clamp(campoPlantilla.getCaretPosition(), 0, texto.length());
        campoPlantilla.setText(texto.substring(0, donde) + marcador + texto.substring(donde));
        campoPlantilla.positionCaret(donde + marcador.length());
        campoPlantilla.requestFocus();
    }

    private void refrescarTamano(EstimacionTamano estimacion) {
        carpetaDestinoController.refrescarEspacio();
        refrescarResumen();
    }

    private void refrescarCompatibilidad(CompatibilidadContenedor compatibilidad) {
        boolean problema = compatibilidad != null && !compatibilidad.compatible()
                && vm.infoProperty().get() != null && !vm.recodificarProperty().get();
        mostrar(cajaAvisoCompat, problema);
        if (problema) {
            etiquetaCompat.setText(compatibilidad.aviso() + " " + compatibilidad.sugerencia());
            etiquetaRemux.setText(Textos.t("descargar.haria_falta_recodificar"));
        } else {
            etiquetaRemux.setText(vm.recodificarProperty().get()
                    ? Textos.t("descargar.se_recodifica_h264")
                    : Textos.t("descargar.se_guarda_sin_recomprimir"));
        }
    }

    private void refrescarNombre() {
        VideoInfo info = vm.infoProperty().get();
        if (info == null) {
            etiquetaNombrePrevisto.setText("");
            return;
        }
        String extension = vm.modoProperty().get() == DownloadMode.SOLO_AUDIO
                ? Optional.ofNullable(vm.formatoAudioProperty().get().extension()).orElse("m4a")
                : vm.recodificarProperty().get() ? "mp4" : vm.contenedorProperty().get().extension();
        etiquetaNombrePrevisto.setText(vm.nombrePrevisto().get() + "." + extension);
    }

    private void prepararCarpeta() {
        carpetaDestinoController.textos(Textos.t("comun.elige_la_carpeta_de_destino"),
                Textos.t("descargar.carpeta_no_existe"));
        carpetaDestinoController.estimacion(() -> {
            EstimacionTamano estimacion = vm.estimacion().get();
            return estimacion != null ? estimacion.bytes() : 0;
        });
        vm.destino(carpetaDestinoController::carpeta);
    }

    private void prepararAvanzado() {
        casillaSubtitulos.selectedProperty().bindBidirectional(vm.descargarSubtitulosProperty());
        casillaSubtitulosAuto.selectedProperty().bindBidirectional(vm.subtitulosAutomaticosProperty());
        casillaIncrustarSubtitulos.selectedProperty().bindBidirectional(vm.incrustarSubtitulosProperty());
        casillaMiniatura.selectedProperty().bindBidirectional(vm.incrustarMiniaturaProperty());
        casillaMetadatos.selectedProperty().bindBidirectional(vm.incrustarMetadatosProperty());
        casillaListaCompleta.selectedProperty().bindBidirectional(vm.listaCompletaProperty());

        comboIdiomaSubtitulos.setItems(vm.subtitulosDisponibles());
        comboIdiomaSubtitulos.setConverter(
                Controles.comoTexto(pista -> pista == null ? "" : pista.etiqueta()));
        comboIdiomaSubtitulos.valueProperty().addListener((obs, antes, ahora) -> {
            if (ahora != null) {
                vm.idiomasSubtitulos().setAll(ahora.codigo());
            }
        });
        comboIdiomaSubtitulos.disableProperty().bind(casillaSubtitulos.selectedProperty().not());
        casillaSubtitulosAuto.disableProperty().bind(casillaSubtitulos.selectedProperty().not());
        casillaIncrustarSubtitulos.disableProperty().bind(casillaSubtitulos.selectedProperty().not());

        comboCookies.getItems().setAll(CookieBrowser.values());
        comboCookies.valueProperty().bindBidirectional(vm.navegadorCookiesProperty());
        casillaListaCompleta.disableProperty().bind(vm.infoProperty().isNull());
    }

    private void prepararMensajes() {
        vm.errorProperty().addListener((obs, antes, ahora) -> {
            boolean hay = ahora != null;
            mostrar(etiquetaError, hay);
            etiquetaError.setText(hay ? ahora.textoCompleto() : "");
        });
        vm.avisoProperty().addListener((obs, antes, ahora) -> {
            boolean hay = ahora != null && !ahora.isBlank();
            mostrar(etiquetaAviso, hay);
            etiquetaAviso.setText(hay ? ahora : "");
        });
        campoUrl.textProperty().bindBidirectional(vm.urlProperty());
    }

    private void prepararBotones() {
        vm.analizandoProperty().addListener((obs, antes, ahora) -> {
            botonAnalizar.setDisable(ahora);
            botonAnalizar.setText(ahora ? Textos.t("descargar.analizando") : "Analizar");
            campoUrl.setDisable(ahora);
            mostrar(esqueleto, ahora);
            if (ahora) {
                mostrar(estadoVacio, false);
                mostrar(tarjetaInfo, false);
                mostrar(tarjetaFormatos, false);
                mostrar(tarjetaSalida, false);
                mostrar(lineaSalida, false);
            }
        });
        vm.infoProperty().addListener((obs, antes, ahora) -> mostrarDatos(ahora));
    }

    /** Muestra lo que depende de haber analizado un enlace. */
    private void mostrarDatos(VideoInfo info) {
        boolean hay = info != null;
        mostrar(estadoVacio, !hay);
        mostrar(tarjetaInfo, hay);
        mostrar(tarjetaFormatos, hay);
        mostrar(tarjetaSalida, hay);
        mostrar(lineaSalida, hay);
        botonDescargar.setDisable(!hay);
        botonAnadirCola.setDisable(!hay);

        if (!hay) {
            return;
        }
        etiquetaTitulo.setText(info.titulo());
        etiquetaCanal.setText(info.canal() + " · " + info.duracionTexto());
        etiquetaMetadatos.setText(metadatosDe(info));
        etiquetaCuentaVideo.setText(Textos.t("comun.cuenta_pistas", info.formatosVideo().size()));
        etiquetaCuentaAudio.setText(Textos.t("comun.cuenta_pistas", info.formatosAudio().size()));
        cargarMiniatura(info.miniaturaUrl());

        if (!vm.subtitulosDisponibles().isEmpty() && comboIdiomaSubtitulos.getValue() == null) {
            vm.subtitulosDisponibles().stream()
                    .filter(p -> vm.idiomasSubtitulos().contains(p.codigo()))
                    .findFirst()
                    .ifPresentOrElse(comboIdiomaSubtitulos::setValue,
                            () -> comboIdiomaSubtitulos.setValue(vm.subtitulosDisponibles().get(0)));
        }
        Animaciones.aparecer(tarjetaInfo);
        Animaciones.aparecer(tarjetaFormatos);
        refrescarNombre();
        refrescarResumen();
    }

    /** Texto corrido con lo que antes eran etiquetas de colores. */
    private static String metadatosDe(VideoInfo info) {
        List<String> partes = new ArrayList<>();
        partes.add(Textos.t("descargar.formatos_de_video", info.formatosVideo().size()));
        partes.add(Textos.t("descargar.formatos_de_audio", info.formatosAudio().size()));
        if (info.formatosVideo().stream().anyMatch(VideoFormat::hdr)) {
            partes.add(Textos.t("descargar.hdr_disponible"));
        }
        if (info.tieneVariosIdiomasDeAudio()) {
            partes.add(Textos.t("descargar.varios_idiomas"));
        }
        if (!info.subtitulos().isEmpty()) {
            partes.add(Textos.t("descargar.subtitulos"));
        }
        if (info.perteneceALista()) {
            partes.add(Textos.t("descargar.lista_de_videos", info.videosEnLista()));
        }
        return String.join(" · ", partes);
    }

    private void cargarMiniatura(String url) {
        if (url == null || url.isBlank()) {
            miniatura.setImage(null);
            return;
        }
        miniatura.setImage(new Image(url, 384, 216, true, true, true));
    }

    private void refrescarResumen() {
        VideoInfo info = vm.infoProperty().get();
        if (info == null) {
            etiquetaResumen.setText("");
            return;
        }
        VideoFormat video = vm.videoEfectivo().get();
        AudioFormat audio = vm.audioEfectivo().get();
        StringBuilder sb = new StringBuilder();
        if (vm.modoProperty().get().incluyeVideo() && video != null) {
            sb.append(video.etiquetaCalidad()).append(" ").append(video.codec().nombreCorto());
        }
        if (vm.modoProperty().get() == DownloadMode.SOLO_AUDIO) {
            sb.append(vm.formatoAudioProperty().get().nombreLegible());
        } else if (vm.modoProperty().get().incluyeAudio() && audio != null) {
            sb.append(" + ").append(audio.codec().nombreCorto());
        }
        EstimacionTamano estimacion = vm.estimacion().get();
        if (estimacion != null && estimacion.conocida()) {
            sb.append("   ").append(Formatos.tamano(estimacion.bytes(), estimacion.aproximado()));
        }
        etiquetaResumen.setText(sb.toString());
    }

    // ------------------------------------------------------------------ acciones

    @FXML
    private void analizar() {
        vm.analizar(this::refrescarResumen);
    }

    /** Escribe un enlace y lo analiza; lo usan los atajos y las opciones de desarrollo. */
    public void cargarUrlInicial(String url) {
        campoUrl.setText(url);
        vm.analizar(() -> {
            refrescarResumen();
            if (Boolean.getBoolean("ud.descargarAlArrancar") && vm.infoProperty().get() != null) {
                encolar(true);
            }
        });
    }

    /** Atajos del módulo: pegar y analizar, y descargar con Intro. */
    public void registrarAtajos(Scene escena) {
        escena.getAccelerators().put(
                new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN),
                this::pegarYAnalizar);
    }

    private void pegarYAnalizar() {
        String portapapeles = Clipboard.getSystemClipboard().getString();
        if (portapapeles == null || portapapeles.isBlank()) {
            return;
        }
        campoUrl.setText(portapapeles.strip());
        analizar();
    }

    @FXML
    private void usarContenedorCompatible() {
        vm.usarContenedorCompatible();
    }

    @FXML
    private void activarRecodificacion() {
        vm.recodificarProperty().set(true);
    }

    @FXML
    private void descargar() {
        encolar(true);
    }

    @FXML
    private void anadirACola() {
        encolar(false);
    }

    private void encolar(boolean irALaCola) {
        Optional<DownloadRequest> peticion = vm.construirPeticion();
        if (peticion.isEmpty()) {
            ErrorAmistoso error = vm.errorProperty().get();
            if (error != null) {
                Dialogos.error(ventana(), Textos.t("descargar.no_se_puede_descargar"), error);
            }
            return;
        }
        if (!contexto.binariosListos()) {
            Dialogos.error(ventana(), Textos.t("descargar.faltan_componentes"), new ErrorAmistoso(
                    Textos.t("descargar.hace_falta_ffmpeg"),
                    Textos.t("descargar.descargalo_desde_ajustes")));
            return;
        }
        contexto.encolarDescarga(peticion.get());
        contexto.recordarCarpeta(peticion.get().carpetaDestino());
        if (irALaCola) {
            alIrALaCola.run();
        }
    }

    /** Recoge en el módulo lo que se haya cambiado en los ajustes. */
    public void refrescarTrasAjustes() {
        carpetaDestinoController.usar(contexto.carpetaDestino());
    }

    public void mostrarError(ErrorAmistoso error) {
        vm.errorProperty().set(error);
    }

    // ------------------------------------------------------------------ utilidades

    /**
     * Columna de una de las dos tablas de pistas. No se pueden ordenar: la fila «Mejor disponible»
     * tiene que quedarse arriba, y en ella las columnas van vacías salvo la de textoMejor.
     */
    private <T extends PistaDescargable> void columna(TableColumn<T, String> columna,
                                                      Function<T, String> texto,
                                                      boolean numerica, String textoMejor) {
        columna.setCellValueFactory(datos -> {
            T fila = datos.getValue();
            return valor(fila.esMejorDisponible()
                    ? (textoMejor != null ? textoMejor : SIN_DATO)
                    : texto.apply(fila));
        });
        columna.setReorderable(false);
        columna.setSortable(false);
        if (numerica) {
            columna.getStyleClass().add("cabecera-dato");
        }
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacio) {
                super.updateItem(texto, vacio);
                getStyleClass().removeAll("columna-dato", "vacio");
                setText(vacio ? null : texto);
                if (vacio || texto == null) {
                    return;
                }
                if (numerica) {
                    getStyleClass().add("columna-dato");
                }
                if (SIN_DATO.equals(texto)) {
                    getStyleClass().add("vacio");
                }
            }
        });
    }

    private static ObservableValue<String> valor(String texto) {
        return new SimpleObjectProperty<>(texto);
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private Window ventana() {
        return campoUrl.getScene() != null ? campoUrl.getScene().getWindow() : null;
    }

    public MainViewModel modelo() {
        return vm;
    }
}
