package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.fx.EnSegundoPlano;
import com.ultimatedownloader.model.calidad.Calidades;
import com.ultimatedownloader.model.calidad.FormatoSalida;
import com.ultimatedownloader.model.calidad.NivelCalidad;
import com.ultimatedownloader.model.convert.MediaInfo;
import com.ultimatedownloader.model.convert.PistaMedia;
import com.ultimatedownloader.model.separacion.ModeloUvr;
import com.ultimatedownloader.model.separacion.SeparacionRequest.ModoVariosModelos;
import com.ultimatedownloader.model.separacion.SeparacionRequest;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.separacion.EntornoPython;
import com.ultimatedownloader.service.separacion.ExtractorAudio;
import com.ultimatedownloader.service.separacion.PresetsSeparacion;
import com.ultimatedownloader.util.Formatos;
import com.ultimatedownloader.util.Textos;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Panel de trabajo de Separar voz: se elige el archivo, uno o varios modelos y lo que se quiere
 * sacar, y se manda a la cola común.
 */
public final class SepararTrabajoController {

    private static final Logger log = LoggerFactory.getLogger(SepararTrabajoController.class);

    /** Formatos que tienen sentido para una pista separada. */
    private static final List<FormatoSalida> FORMATOS =
            List.of(FormatoSalida.FLAC, FormatoSalida.WAV, FormatoSalida.MP3,
                    FormatoSalida.M4A, FormatoSalida.OPUS, FormatoSalida.AIFF);

    /** Frecuencias de salida habituales; 0 significa «la del original». */
    private static final List<Integer> FRECUENCIAS = List.of(0, 44100, 48000, 88200, 96000);

    private final Contexto contexto;
    private final PresetsSeparacion presets = new PresetsSeparacion();

    private Runnable alIrALaCola = () -> { };
    private Runnable alBorrarMotor = () -> { };
    private Runnable alPedirConsola = () -> { };

    private Path archivo;
    private MediaInfo info;

    // ---- motor ----
    @FXML private Label etiquetaVersionMotor;
    @FXML private Label etiquetaDispositivo;
    @FXML private VBox cajaAvisoDispositivo;
    @FXML private Label etiquetaAvisoDispositivo;
    @FXML private Button botonComprobarMotor;
    @FXML private Button botonBorrarMotor;

    // ---- archivo ----
    @FXML private VBox zonaSoltarSeparar;
    @FXML private Label etiquetaArchivo;
    @FXML private Label etiquetaFichaArchivo;

    // ---- modelos ----
    @FXML private SepararModelosController modelosController;

    // ---- qué sacar ----
    @FXML private ToggleGroup grupoPistas;
    @FXML private ToggleButton pistaTodas;
    @FXML private ToggleButton pistaVoz;
    @FXML private ToggleButton pistaInstrumental;
    @FXML private ComboBox<FormatoSalida> comboFormato;
    @FXML private ToggleGroup grupoCalidadSeparacion;
    @FXML private ToggleButton calidadMaxima;
    @FXML private ToggleButton calidadMedia;
    @FXML private ToggleButton calidadMinima;
    @FXML private Label etiquetaCalidad;
    @FXML private ComboBox<Integer> comboFrecuencia;
    @FXML private Label etiquetaFrecuencia;
    @FXML private VBox cajaAvisos;
    @FXML private CheckBox casillaMuestra;
    @FXML private TextField campoMuestraSegundos;
    @FXML private TextField campoMuestraDesde;
    @FXML private Label etiquetaMuestra;
    @FXML private CheckBox casillaReemplazarVideo;

    // ---- avanzado ----
    @FXML private SepararAvanzadoController avanzadoController;

    // ---- presets ----
    @FXML private ComboBox<PresetsSeparacion.Preset> comboPresets;

    // ---- destino ----
    @FXML private CarpetaDestinoController carpetaDestinoController;

    @FXML private Label etiquetaResumen;
    @FXML private Button botonSeparar;

    public SepararTrabajoController(Contexto contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        modelosController.alCambiar(this::refrescarTodo);
        prepararSalida();
        avanzadoController.alCambiar(this::refrescarTodo);
        prepararSoltar();
        prepararCarpeta();
        refrescarPresets();
        refrescarTodo();
    }

    // ------------------------------------------------------------------ enlaces con el armazón

    public void alIrALaCola(Runnable accion) {
        this.alIrALaCola = accion != null ? accion : () -> { };
    }

    public void alBorrarMotor(Runnable accion) {
        this.alBorrarMotor = accion != null ? accion : () -> { };
    }

    public void alPedirConsola(Runnable accion) {
        modelosController.alPedirConsola(accion != null ? accion : () -> { });
    }

    /** Lo llama el módulo cuando este panel pasa a ser el visible. */
    public void alMostrarse() {
        comprobarMotor();
        modelosController.cargar();
    }

    /**
     * Carga un archivo y lo manda a separar en cuanto esté el catálogo; es para desarrollo
     * (gradlew run -Pseparar=...), que si no habría que ir pulsando a mano.
     */
    public void separarAlArrancar(Path ruta) {
        establecerArchivo(ruta);
        modelosController.cargarYLuego(this::separar);
    }

    /** Vuelve a leer la carpeta de destino tras tocar los ajustes. */
    public void refrescarTrasAjustes() {
        carpetaDestinoController.usar(contexto.carpetaDestino());
    }

    // ------------------------------------------------------------------ motor

    @FXML
    private void comprobarMotor() {
        botonComprobarMotor.setDisable(true);
        etiquetaDispositivo.setText(Textos.t("comun.comprobando"));
        EnSegundoPlano.calcular(contexto.tareas(), () -> entorno().comprobar(),
                estado -> {
                    botonComprobarMotor.setDisable(false);
                    etiquetaVersionMotor.setText(estado.version() != null
                            ? "audio-separator " + estado.version() : "");
                    etiquetaDispositivo.setText(estado.resumenDispositivo());
                    mostrarAviso(cajaAvisoDispositivo, etiquetaAvisoDispositivo, estado.aviso());
                },
                causa -> {
                    botonComprobarMotor.setDisable(false);
                    etiquetaDispositivo.setText(Textos.t("comun.no_se_ha_podido_comprobar"));
                });
    }

    @FXML
    private void borrarMotor() {
        boolean seguro = Dialogos.confirmar(ventana(), Textos.t("separar.borrar_motor_titulo"),
                Textos.t("separar.borrar_motor_mensaje"),
                Textos.t("separar.borrar_motor_si"), Textos.t("separar.borrar_motor_no"));
        if (!seguro) {
            return;
        }
        botonBorrarMotor.setDisable(true);
        EnSegundoPlano.hacer(contexto.tareas(), () -> entorno().desinstalar(),
                () -> {
                    botonBorrarMotor.setDisable(false);
                    modelosController.olvidar();
                    alBorrarMotor.run();
                },
                causa -> {
                    botonBorrarMotor.setDisable(false);
                    if (causa instanceof AppException fallo) {
                        Dialogos.error(ventana(), Textos.t("shell.separar_voz"), fallo.error());
                    }
                });
    }

    // ------------------------------------------------------------------ archivo

    @FXML
    private void elegirArchivo() {
        FileChooser selector = new FileChooser();
        selector.setTitle(Textos.t("separar.elige_la_cancion"));
        selector.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(Textos.t("comun.audio_y_video"),
                        "*.mp3", "*.flac", "*.wav", "*.m4a", "*.aac", "*.ogg", "*.opus", "*.aiff",
                        "*.mkv", "*.mp4", "*.mov", "*.webm", "*.avi"),
                new FileChooser.ExtensionFilter(Textos.t("comun.todos_los_archivos"), "*.*"));
        Path destino = carpetaDestinoController.carpeta();
        if (destino != null && Files.isDirectory(destino)) {
            selector.setInitialDirectory(destino.toFile());
        }
        File elegido = selector.showOpenDialog(ventana());
        if (elegido != null) {
            establecerArchivo(elegido.toPath());
        }
    }

    /** Acepta archivos soltados sobre el panel. */
    private void prepararSoltar() {
        zonaSoltarSeparar.setOnDragOver(evento -> {
            if (evento.getDragboard().hasFiles()) {
                evento.acceptTransferModes(TransferMode.COPY);
                if (!zonaSoltarSeparar.getStyleClass().contains("soltando")) {
                    zonaSoltarSeparar.getStyleClass().add("soltando");
                }
            }
            evento.consume();
        });
        zonaSoltarSeparar.setOnDragExited(evento -> {
            zonaSoltarSeparar.getStyleClass().remove("soltando");
            evento.consume();
        });
        zonaSoltarSeparar.setOnDragDropped(this::soltar);
    }

    private void soltar(DragEvent evento) {
        zonaSoltarSeparar.getStyleClass().remove("soltando");
        List<File> archivos = evento.getDragboard().getFiles();
        boolean hay = archivos != null && !archivos.isEmpty();
        if (hay) {
            establecerArchivo(archivos.get(0).toPath());
        }
        evento.setDropCompleted(hay);
        evento.consume();
    }

    /** Guarda el archivo elegido y le pide los datos a ffprobe en segundo plano. */
    public void establecerArchivo(Path ruta) {
        archivo = ruta;
        info = null;
        etiquetaArchivo.setText(ruta.toString());
        ficha(Textos.t("separar.leyendo_el_archivo"));
        refrescarTodo();

        if (contexto.probe() == null) {
            ficha(null);
            return;
        }
        EnSegundoPlano.calcular(contexto.tareas(), () -> contexto.probe().analizar(ruta),
                leido -> {
                    info = leido;
                    ficha(resumenDelArchivo(leido));
                    refrescarTodo();
                },
                causa -> {
                    log.debug("No se ha podido leer {}", ruta);
                    ficha(Textos.t("separar.no_se_ha_podido_leer"));
                });
    }

    /** La ficha solo ocupa sitio cuando tiene algo que contar. */
    private void ficha(String texto) {
        boolean hay = texto != null && !texto.isBlank();
        etiquetaFichaArchivo.setText(hay ? texto : "");
        mostrar(etiquetaFichaArchivo, hay);
    }

    private static String resumenDelArchivo(MediaInfo info) {
        StringBuilder sb = new StringBuilder(info.resumen());
        info.primeraDeAudio().ifPresent(audio -> sb.append(" · ").append(audio.resumen()));
        if (info.primeraDeVideo().isPresent()) {
            sb.append(" · ").append(Textos.t("separar.con_video"));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ salida

    private void prepararSalida() {
        comboFormato.getItems().setAll(FORMATOS);
        comboFormato.setConverter(
                Controles.comoTexto(formato -> formato == null ? "" : formato.nombreLegible()));
        comboFormato.setValue(FormatoSalida.FLAC);
        comboFormato.valueProperty().addListener((o, a, b) -> refrescarTodo());

        comboFrecuencia.getItems().setAll(FRECUENCIAS);
        comboFrecuencia.setConverter(Controles.comoTexto(hz -> hz == null || hz == 0
                ? Textos.t("separar.la_del_original")
                : Formatos.frecuencia(hz)));
        comboFrecuencia.setValue(0);
        comboFrecuencia.valueProperty().addListener((o, a, b) -> refrescarTodo());

        grupoCalidadSeparacion.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                grupoCalidadSeparacion.selectToggle(antes);
                return;
            }
            refrescarTodo();
        });
        grupoPistas.selectedToggleProperty().addListener((o, antes, ahora) -> {
            if (ahora == null) {
                grupoPistas.selectToggle(antes);
                return;
            }
            refrescarTodo();
        });
        casillaMuestra.selectedProperty().addListener((o, a, b) -> refrescarTodo());
        campoMuestraDesde.textProperty().addListener((o, a, b) -> refrescarTodo());
        campoMuestraSegundos.textProperty().addListener((o, a, b) -> refrescarTodo());
        casillaReemplazarVideo.selectedProperty().addListener((o, a, b) -> refrescarTodo());
    }

    private NivelCalidad nivel() {
        if (grupoCalidadSeparacion.getSelectedToggle() == calidadMedia) {
            return NivelCalidad.MEDIA;
        }
        if (grupoCalidadSeparacion.getSelectedToggle() == calidadMinima) {
            return NivelCalidad.MINIMA;
        }
        return NivelCalidad.MAXIMA;
    }

    private String pistaElegida() {
        if (grupoPistas.getSelectedToggle() == pistaVoz) {
            return "vocals";
        }
        if (grupoPistas.getSelectedToggle() == pistaInstrumental) {
            return "instrumental";
        }
        return null;
    }

    private SeparacionRequest.Muestra muestra() {
        if (!casillaMuestra.isSelected()) {
            return null;
        }
        int segundos = Controles.entero(campoMuestraSegundos, 30);
        int desde = Controles.entero(campoMuestraDesde, 0);
        return segundos > 0 ? new SeparacionRequest.Muestra(desde, segundos) : null;
    }

    // ------------------------------------------------------------------ presets

    private void refrescarPresets() {
        PresetsSeparacion.Preset elegido = comboPresets.getValue();
        comboPresets.getItems().setAll(presets.todos());
        if (elegido != null) {
            comboPresets.setValue(presets.porNombre(elegido.nombre()).orElse(null));
        }
    }

    @FXML
    private void aplicarPreset() {
        PresetsSeparacion.Preset preset = comboPresets.getValue();
        if (preset == null) {
            return;
        }
        modelosController.aplicar(preset.modelos(), preset.modo(), preset.algoritmo());
        grupoPistas.selectToggle(switch (preset.pista() == null ? "" : preset.pista()) {
            case "vocals" -> pistaVoz;
            case "instrumental" -> pistaInstrumental;
            default -> pistaTodas;
        });
        comboFormato.setValue(preset.formato());
        grupoCalidadSeparacion.selectToggle(switch (preset.nivel()) {
            case MEDIA -> calidadMedia;
            case MINIMA -> calidadMinima;
            default -> calidadMaxima;
        });
        comboFrecuencia.setValue(preset.frecuencia());
        avanzadoController.aplicar(preset.opciones());
        refrescarTodo();
    }

    @FXML
    private void guardarPreset() {
        if (modelosController.seleccionados().isEmpty()) {
            return;
        }
        TextInputDialog dialogo = new TextInputDialog(
                comboPresets.getValue() != null ? comboPresets.getValue().nombre() : "");
        dialogo.initOwner(ventana());
        dialogo.setTitle(Textos.t("separar.guardar_los_ajustes"));
        dialogo.setHeaderText(null);
        dialogo.setContentText(Textos.t("separar.ponles_un_nombre"));
        Ventanas.preparar(dialogo, contexto.ajustes().isTemaOscuro());

        Optional<String> nombre = dialogo.showAndWait().map(String::strip).filter(n -> !n.isEmpty());
        if (nombre.isEmpty()) {
            return;
        }
        presets.guardar(new PresetsSeparacion.Preset(nombre.get(),
                modelosController.seleccionados().stream().map(ModeloUvr::fichero).toList(),
                modelosController.modo(), modelosController.algoritmo(), pistaElegida(), comboFormato.getValue(), nivel(),
                comboFrecuencia.getValue() != null ? comboFrecuencia.getValue() : 0,
                avanzadoController.opciones()));
        refrescarPresets();
        presets.porNombre(nombre.get()).ifPresent(comboPresets::setValue);
    }

    @FXML
    private void borrarPreset() {
        PresetsSeparacion.Preset preset = comboPresets.getValue();
        if (preset == null) {
            return;
        }
        presets.borrar(preset.nombre());
        refrescarPresets();
    }

    // ------------------------------------------------------------------ destino

    private void prepararCarpeta() {
        carpetaDestinoController.textos(Textos.t("separar.elige_donde_dejar"),
                Textos.t("separar.carpeta_no_existe"));
        carpetaDestinoController.carpetaProperty()
                .addListener((obs, antes, ahora) -> refrescarTodo());
    }

    // ------------------------------------------------------------------ separar

    @FXML
    private void separar() {
        List<ModeloUvr> elegidos = modelosController.seleccionados();
        Path destino = carpetaDestinoController.carpeta();
        if (archivo == null || elegidos.isEmpty() || destino == null) {
            return;
        }
        contexto.encolarSeparacion(new SeparacionRequest(
                archivo, destino,
                elegidos.stream().map(ModeloUvr::fichero).toList(),
                modelosController.modo(), modelosController.algoritmo(), List.of(), pistaElegida(), comboFormato.getValue(), nivel(),
                comboFrecuencia.getValue() != null ? comboFrecuencia.getValue() : 0,
                avanzadoController.opciones(), muestra(),
                casillaReemplazarVideo.isSelected()));
        alIrALaCola.run();
    }

    // ------------------------------------------------------------------ refrescos

    private void refrescarTodo() {
        List<ModeloUvr> elegidos = modelosController.seleccionados();

        // Con una muestra la pista separada dura treinta segundos y el vídeo entero: no tiene
        // sentido montarlos juntos, así que la opción se apaga y se explica por qué.
        boolean esVideo = archivo != null && ExtractorAudio.esVideo(archivo);
        boolean puedeReemplazar = esVideo && !casillaMuestra.isSelected();
        casillaReemplazarVideo.setDisable(!puedeReemplazar);
        if (!puedeReemplazar) {
            casillaReemplazarVideo.setSelected(false);
        }
        casillaReemplazarVideo.setText(esVideo && casillaMuestra.isSelected()
                ? Textos.t("separar.reemplazar_video_no_con_muestra")
                : Textos.t("separar_trabajo.dejar_ademas_una_copia_del"));
        campoMuestraDesde.setDisable(!casillaMuestra.isSelected());
        campoMuestraSegundos.setDisable(!casillaMuestra.isSelected());

        refrescarCalidad();
        refrescarMuestra();
        refrescarAvisos();
        refrescarResumen(elegidos);
    }

    private void refrescarCalidad() {
        FormatoSalida formato = comboFormato.getValue();
        if (formato == null) {
            return;
        }
        etiquetaCalidad.setText(Calidades.de(formato, nivel()).resumen());

        Integer frecuencia = comboFrecuencia.getValue();
        PistaMedia audio = info != null ? info.primeraDeAudio().orElse(null) : null;
        if (frecuencia == null || frecuencia == 0) {
            etiquetaFrecuencia.setText(audio != null && audio.frecuenciaHz() > 0
                    ? Textos.t("separar.mantiene_original_con", audio.frecuenciaTexto())
                    : Textos.t("separar.mantiene_original"));
        } else {
            etiquetaFrecuencia.setText("");
        }
    }

    private void refrescarMuestra() {
        if (!casillaMuestra.isSelected()) {
            etiquetaMuestra.setText("");
            return;
        }
        int desde = Controles.entero(campoMuestraDesde, 0);
        int segundos = Controles.entero(campoMuestraSegundos, 30);
        etiquetaMuestra.setText(Textos.t("separar.muestra_rango",
                Formatos.duracionReloj(desde), Formatos.duracionReloj(desde + (long) segundos)));
    }

    /**
     * Avisos de calidad, con el mismo criterio que en Convertir: no se inventa calidad. Subir la
     * frecuencia de la salida no añade nada que no estuviera en el original.
     */
    private void refrescarAvisos() {
        List<String> avisos = new ArrayList<>();
        Integer frecuencia = comboFrecuencia.getValue();
        PistaMedia audio = info != null ? info.primeraDeAudio().orElse(null) : null;

        if (frecuencia != null && frecuencia > 0 && audio != null
                && audio.frecuenciaHz() > 0 && frecuencia > audio.frecuenciaHz()) {
            avisos.add(Textos.t("separar.aviso_frecuencia", Formatos.frecuencia(frecuencia),
                    Formatos.frecuencia(audio.frecuenciaHz())));
        }
        if (comboFormato.getValue() != null && !comboFormato.getValue().admiteSinPerdida()) {
            avisos.add(Textos.t("separar.aviso_formato_con_perdida"));
        }
        if (avanzadoController.opciones().usarFp16()) {
            avisos.add(Textos.t("separar.aviso_fp16"));
        }

        mostrar(cajaAvisos, !avisos.isEmpty());
        cajaAvisos.getChildren().clear();
        for (String aviso : avisos) {
            Label etiqueta = new Label(aviso);
            etiqueta.setWrapText(true);
            etiqueta.getStyleClass().add("texto-2");
            cajaAvisos.getChildren().add(etiqueta);
        }
    }

    private void refrescarResumen(List<ModeloUvr> elegidos) {
        Path destino = carpetaDestinoController.carpeta();
        botonSeparar.setDisable(archivo == null || elegidos.isEmpty() || destino == null
                || !Files.isDirectory(destino));
        if (archivo == null) {
            etiquetaResumen.setText(Textos.t("separar.elige_primero_archivo"));
            return;
        }
        if (elegidos.isEmpty()) {
            etiquetaResumen.setText(Textos.t("separar.elige_al_menos_un_modelo"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        String pista = pistaElegida();
        sb.append(pista == null ? Textos.t("separar.voz_e_instrumental") : ModeloUvr.nombrePista(pista));
        sb.append(" → ").append(comboFormato.getValue().nombreLegible());
        sb.append(" · ").append(nivel().nombreLegible());
        if (elegidos.size() > 1) {
            sb.append(" · ").append(Textos.t(
                    modelosController.modo() == ModoVariosModelos.MEZCLA
                            ? "separar.resumen_mezcla" : "separar.resumen_por_separado",
                    elegidos.size()));
        }
        if (casillaMuestra.isSelected()) {
            sb.append(" · ").append(Textos.t("separar.resumen_muestra",
                    Controles.entero(campoMuestraSegundos, 30)));
        }
        etiquetaResumen.setText(sb.toString());
    }

    // ------------------------------------------------------------------ apoyo

    private EntornoPython entorno() {
        return contexto.entornoPython();
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private static void mostrarAviso(VBox caja, Label etiqueta, String texto) {
        boolean hay = texto != null && !texto.isBlank();
        etiqueta.setText(hay ? texto : "");
        mostrar(caja, hay);
    }

    /** Un campo vacío significa «no tocar»: por eso devuelve null y no un cero. */
    private Window ventana() {
        return etiquetaResumen.getScene() != null ? etiquetaResumen.getScene().getWindow() : null;
    }
}
