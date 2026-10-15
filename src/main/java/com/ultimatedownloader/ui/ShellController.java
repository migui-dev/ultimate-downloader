package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.service.Plataforma;
import com.ultimatedownloader.ui.iconos.Iconos;
import com.ultimatedownloader.ui.iconos.Lucide;
import com.ultimatedownloader.util.Textos;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Armazón de la aplicación: barra lateral, módulo activo y consola. Cada módulo es un panel
 * del StackPane central, así que cambiar de uno a otro no reconstruye ni pierde nada.
 */
public final class ShellController {

    private static final Logger log = LoggerFactory.getLogger(ShellController.class);

    private final Contexto contexto;

    @FXML private ToggleGroup grupoNavegacion;
    @FXML private ToggleButton navDescargar;
    @FXML private ToggleButton navConvertir;
    @FXML private ToggleButton navSeparar;
    @FXML private ToggleButton navCola;
    @FXML private Button botonConsola;
    @FXML private Button botonAjustes;
    @FXML private Button botonTema;

    @FXML private Label tituloModulo;
    @FXML private Label subtituloModulo;
    @FXML private StackPane paneles;
    @FXML private Node descargar;
    @FXML private Node convertir;
    @FXML private Node separar;
    @FXML private Node cola;
    @FXML private Node consola;

    @FXML private MainController descargarController;
    @FXML private ConvertirController convertirController;
    @FXML private SepararController separarController;
    @FXML private QueueController colaController;
    @FXML private ConsolaController consolaController;

    public ShellController(Contexto contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        prepararIconos();
        prepararNavegacion();

        mostrarConsola(false);
        consolaController.alCerrar(() -> mostrarConsola(false));

        if (colaController != null) {
            navCola.textProperty().bind(colaController.tituloPestana());
        }
        if (descargarController != null) {
            descargarController.alIrALaCola(this::irALaCola);
        }
        if (convertirController != null) {
            convertirController.alIrALaCola(this::irALaCola);
        }
        if (separarController != null) {
            separarController.alPedirConsola(() -> mostrarConsola(true));
            separarController.alIrALaCola(this::irALaCola);
        }
        seleccionar(navDescargar);
        comprobarBinarios();
    }

    /** Si falta yt-dlp o ffmpeg, se abre el asistente en cuanto está la ventana. */
    private void comprobarBinarios() {
        if (contexto.binariosListos()) {
            return;
        }
        Platform.runLater(() -> {
            SetupController.resolverComponentes(ventana(), contexto);
            if (contexto.binariosListos() || descargarController == null) {
                return;
            }
            // Plataforma.ejecutable pone el «.exe» solo en Windows: escrito a mano salía
            // «No se encuentra yt-dlp.exe» también en Linux y en macOS.
            StringBuilder falta = new StringBuilder();
            if (contexto.rutaYtDlp().isEmpty()) {
                falta.append(Plataforma.ejecutable("yt-dlp"));
            }
            if (contexto.rutaFfmpeg().isEmpty()) {
                falta.append(falta.isEmpty() ? "" : " " + Textos.t("comun.y") + " ")
                        .append(Plataforma.ejecutable("ffmpeg"));
            }
            descargarController.mostrarError(new ErrorAmistoso(
                    Textos.t("shell.no_se_encuentra", falta.toString()),
                    Textos.t("shell.abre_ajustes_para_descargarlo")));
        });
    }

    /** Abre una pantalla por su nombre; lo usa la opción de desarrollo -Dud.abrir. */
    public void abrirPantalla(String nombre) {
        switch (nombre) {
            case "ajustes" -> abrirAjustes();
            case "componentes" -> SetupController.mostrar(ventana(), contexto);
            case "cola" -> irALaCola();
            case "convertir" -> seleccionar(navConvertir);
            case "separar" -> seleccionar(navSeparar);
            case "consola" -> mostrarConsola(true);
            default -> log.warn("Pantalla desconocida: {}", nombre);
        }
    }

    private void prepararIconos() {
        navDescargar.setGraphic(Iconos.de(Lucide.DESCARGAR));
        navConvertir.setGraphic(Iconos.de(Lucide.CONVERTIR));
        navSeparar.setGraphic(Iconos.de(Lucide.SEPARAR));
        navCola.setGraphic(Iconos.de(Lucide.COLA));
        botonConsola.setGraphic(Iconos.de(Lucide.CONSOLA));
        botonAjustes.setGraphic(Iconos.de(Lucide.AJUSTES));
        actualizarBotonTema();
    }

    private void prepararNavegacion() {
        grupoNavegacion.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                // La barra lateral siempre tiene un módulo activo.
                grupoNavegacion.selectToggle(antes);
                return;
            }
            mostrarModulo((ToggleButton) ahora);
        });
    }

    private void seleccionar(ToggleButton boton) {
        grupoNavegacion.selectToggle(boton);
        mostrarModulo(boton);
    }

    private void mostrarModulo(ToggleButton boton) {
        Node panel;
        String titulo;
        String subtitulo;

        if (boton == navConvertir) {
            panel = convertir;
            titulo = Textos.t("comun.convertir");
            subtitulo = Textos.t("shell.subtitulo_convertir");
        } else if (boton == navSeparar) {
            panel = separar;
            titulo = Textos.t("shell.separar_voz");
            subtitulo = Textos.t("shell.subtitulo_separar");
        } else if (boton == navCola) {
            panel = cola;
            titulo = Textos.t("shell.cola");
            subtitulo = Textos.t("shell.subtitulo_cola");
        } else {
            panel = descargar;
            titulo = Textos.t("comun.descargar");
            subtitulo = Textos.t("shell.subtitulo_descargar");
        }

        tituloModulo.setText(titulo);
        subtituloModulo.setText(subtitulo);
        for (Node hijo : paneles.getChildren()) {
            boolean activo = hijo == panel;
            hijo.setVisible(activo);
            hijo.setManaged(activo);
        }
        aparecer(panel);
    }

    /** Fundido corto al cambiar de módulo; nada de rebotes. */
    private static void aparecer(Node panel) {
        FadeTransition fundido = new FadeTransition(Duration.millis(120), panel);
        fundido.setFromValue(0.6);
        fundido.setToValue(1);
        fundido.play();
    }

    // ------------------------------------------------------------------ acciones

    @FXML
    private void alternarConsola() {
        mostrarConsola(!consola.isVisible());
    }

    private void mostrarConsola(boolean visible) {
        consola.setVisible(visible);
        consola.setManaged(visible);
        botonConsola.setGraphic(Iconos.de(visible ? Lucide.PANEL_CERRAR : Lucide.CONSOLA));
        if (visible) {
            aparecer(consola);
        }
    }

    @FXML
    private void abrirAjustes() {
        boolean idiomaCambiado = SettingsController.mostrar(ventana(), contexto, () -> {
            actualizarBotonTema();
            if (descargarController != null) {
                descargarController.refrescarTrasAjustes();
            }
            if (convertirController != null) {
                convertirController.refrescarTrasAjustes();
            }
            if (separarController != null) {
                separarController.refrescarTrasAjustes();
            }
            aplicarTema(contexto.ajustes().isTemaOscuro());
        });
        if (idiomaCambiado) {
            contexto.recargarInterfaz();
        }
    }

    @FXML
    private void alternarTema() {
        boolean oscuro = !contexto.ajustes().isTemaOscuro();
        contexto.ajustes().setTemaOscuro(oscuro);
        contexto.servicioAjustes().guardar();
        aplicarTema(oscuro);
    }

    private void aplicarTema(boolean oscuro) {
        Ventanas.aplicarTemaATodas(oscuro);
        actualizarBotonTema();
    }

    private void actualizarBotonTema() {
        boolean oscuro = contexto.ajustes().isTemaOscuro();
        botonTema.setText(Textos.t(oscuro ? "shell.tema_claro" : "shell.tema_oscuro"));
        botonTema.setGraphic(Iconos.de(oscuro ? Lucide.SOL : Lucide.LUNA));
    }

    /** Registra los atajos de teclado una vez la escena existe. */
    public void registrarAtajos() {
        var escena = paneles.getScene();
        if (escena == null) {
            return;
        }
        escena.getAccelerators().put(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                this::alternarConsola);
        escena.getAccelerators().put(
                new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN),
                () -> seleccionar(navDescargar));
        escena.getAccelerators().put(
                new KeyCodeCombination(KeyCode.J, KeyCombination.CONTROL_DOWN),
                () -> seleccionar(navCola));
        if (descargarController != null) {
            descargarController.registrarAtajos(escena);
        }
    }

    private Window ventana() {
        return paneles.getScene() != null ? paneles.getScene().getWindow() : null;
    }

    /** Lleva al usuario a la cola, por ejemplo tras lanzar una descarga. */
    public void irALaCola() {
        seleccionar(navCola);
    }

    public MainController moduloDescargar() {
        return descargarController;
    }

    public ConvertirController moduloConvertir() {
        return convertirController;
    }

    public SepararController moduloSeparar() {
        return separarController;
    }
}
