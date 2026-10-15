package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.fx.EnSegundoPlano;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.BinaryManager;
import com.ultimatedownloader.service.Plataforma;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asistente que aparece cuando faltan yt-dlp o ffmpeg. Los baja de su fuente oficial a la
 * carpeta de datos, que siempre es escribible, o deja indicar una ruta si ya se tienen.
 */
public final class SetupController {

    private static final Logger log = LoggerFactory.getLogger(SetupController.class);

    private final Contexto contexto;

    @FXML private Label etiquetaTitulo;
    @FXML private Label estadoYtDlp;
    @FXML private Label estadoFfmpeg;
    @FXML private Button botonRutaYtDlp;
    @FXML private Button botonRutaFfmpeg;
    @FXML private Label etiquetaProgreso;
    @FXML private ProgressBar barraProgreso;
    @FXML private Hyperlink enlaceCarpeta;
    @FXML private Button botonContinuar;
    @FXML private Button botonDescargar;

    private Stage ventana;

    /** Cierra la ventana en cuanto la descarga termina bien; solo en el modo automático. */
    private boolean cerrarAlTerminar;

    public SetupController(Contexto contexto) {
        this.contexto = contexto;
    }

    /** Abre el asistente como ventana modal y espera a que se cierre. */
    public static void mostrar(Window propietario, Contexto contexto) {
        abrir(propietario, contexto, false);
    }

    /**
     * Lo mismo, pero sin esperar a que nadie pulse nada: empieza a bajar lo que falte y se
     * cierra solo al terminar. Es lo que se hace al arrancar, para que la primera vez la
     * aplicación se prepare sola; solo se queda abierto si algo falla o si hay algo que el
     * usuario tiene que resolver a mano, como ffmpeg en macOS.
     */
    public static void resolverComponentes(Window propietario, Contexto contexto) {
        abrir(propietario, contexto, true);
    }

    private static void abrir(Window propietario, Contexto contexto, boolean automatico) {
        try {
            FXMLLoader cargador = new FXMLLoader(
                    SetupController.class.getResource("/com/ultimatedownloader/view/setup.fxml"),
                    Textos.paquete());
            cargador.setControllerFactory(tipo -> new SetupController(contexto));
            Scene escena = new Scene(cargador.load());

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.APPLICATION_MODAL);
            ventana.setTitle(Textos.t("componentes.ventana"));
            ventana.setScene(escena);
            ventana.setResizable(false);
            Ventanas.preparar(ventana, contexto.ajustes().isTemaOscuro());

            SetupController controlador = cargador.getController();
            controlador.ventana = ventana;
            if (automatico && controlador.sePuedeResolverSolo()) {
                controlador.cerrarAlTerminar = true;
                // Al estar ya en pantalla: si la descarga falla enseguida, el aviso de error
                // necesita una ventana mostrada a la que colgarse.
                ventana.setOnShown(evento -> controlador.descargarTodo());
            }
            ventana.showAndWait();
        } catch (IOException e) {
            log.error("No se ha podido abrir el asistente de componentes", e);
        }
    }

    @FXML
    private void initialize() {
        refrescarEstado();
    }

    /**
     * true si todo lo que falta se puede bajar sin intervención. En macOS no hay compilación
     * oficial de ffmpeg: si es el que falta, el asistente se queda abierto explicándolo en vez
     * de lanzar una descarga que va a fallar.
     */
    private boolean sePuedeResolverSolo() {
        return !contexto.binariosListos()
                && (contexto.rutaFfmpeg().isPresent() || BinaryManager.hayDescargaDeFfmpeg());
    }

    private void refrescarEstado() {
        contexto.localizarBinarios();
        estadoYtDlp.setText(contexto.rutaYtDlp()
                .map(r -> Textos.t("componentes.listo") + " · " + r)
                .orElse(Textos.t("comun.no_encontrado")));
        estadoFfmpeg.setText(contexto.rutaFfmpeg()
                .map(r -> Textos.t("componentes.listo") + " · " + r)
                .orElse(Textos.t("comun.no_encontrado")));

        boolean falta = !contexto.binariosListos();
        etiquetaTitulo.setText(Textos.t(falta ? "descargar.faltan_componentes" : "componentes.titulo"));
        botonDescargar.setDisable(!falta);
        botonContinuar.setText(Textos.t(falta ? "componentes.continuar_sin_descargar" : "comun.cerrar"));
        if (!falta) {
            etiquetaProgreso.setText(Textos.t("componentes.todo_listo"));
        }
    }

    // ------------------------------------------------------------------ acciones

    @FXML
    private void descargarTodo() {
        botonDescargar.setDisable(true);
        botonRutaYtDlp.setDisable(true);
        botonRutaFfmpeg.setDisable(true);
        barraProgreso.setVisible(true);
        barraProgreso.setManaged(true);

        EnSegundoPlano.hacer(contexto.tareas(),
                () -> {
                    if (contexto.rutaYtDlp().isEmpty()) {
                        anunciar(Textos.t("componentes.descargando_ytdlp"));
                        contexto.binarios().descargarYtDlp(this::avanzar);
                    }
                    if (contexto.rutaFfmpeg().isEmpty()) {
                        anunciar(Textos.t("componentes.descargando_ffmpeg"));
                        contexto.binarios().descargarFfmpeg(this::avanzar);
                    }
                },
                () -> {
                    barraProgreso.setProgress(1);
                    etiquetaProgreso.setText(Textos.t("componentes.instalados"));
                    botonRutaYtDlp.setDisable(false);
                    botonRutaFfmpeg.setDisable(false);
                    refrescarEstado();
                    if (cerrarAlTerminar && contexto.binariosListos() && ventana != null) {
                        ventana.close();
                    }
                },
                causa -> {
                    log.warn("Fallo al descargar los componentes", causa);
                    // Si falla, la ventana se queda: ahí están el reintento y las rutas a mano.
                    cerrarAlTerminar = false;
                    barraProgreso.setVisible(false);
                    barraProgreso.setManaged(false);
                    botonDescargar.setDisable(false);
                    botonRutaYtDlp.setDisable(false);
                    botonRutaFfmpeg.setDisable(false);
                    etiquetaProgreso.setText("");
                    Dialogos.error(ventana, Textos.t("componentes.no_se_han_podido_descargar"),
                            causa instanceof AppException app
                                    ? app.error()
                                    : new ErrorAmistoso(
                                            Textos.t("componentes.no_se_han_podido_descargar") + ".",
                                            Textos.t("componentes.comprueba_conexion"),
                                            String.valueOf(causa)));
                });
    }

    private void anunciar(String texto) {
        Platform.runLater(() -> etiquetaProgreso.setText(texto));
    }

    /** Avance de la descarga; negativo cuando el servidor no dice cuánto ocupa. */
    private void avanzar(double fraccion) {
        Platform.runLater(() -> barraProgreso.setProgress(
                fraccion >= 0 ? fraccion : ProgressBar.INDETERMINATE_PROGRESS));
    }

    @FXML
    private void elegirYtDlp() {
        elegirEjecutable(Plataforma.ejecutable("yt-dlp")).ifPresent(ruta -> {
            contexto.ajustes().setRutaYtDlp(ruta.toString());
            contexto.servicioAjustes().guardar();
            refrescarEstado();
        });
    }

    @FXML
    private void elegirFfmpeg() {
        elegirEjecutable(Plataforma.ejecutable("ffmpeg")).ifPresent(ruta -> {
            contexto.ajustes().setRutaFfmpeg(ruta.toString());
            contexto.servicioAjustes().guardar();
            refrescarEstado();
        });
    }

    private java.util.Optional<Path> elegirEjecutable(String nombre) {
        FileChooser selector = new FileChooser();
        selector.setTitle(Textos.t("componentes.elige_ejecutable", nombre));
        // Fuera de Windows los programas no tienen extensión, así que un filtro por nombre
        // escondería el archivo que se busca.
        if (Plataforma.esWindows()) {
            selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    Textos.t("componentes.ejecutable", nombre), nombre));
        }
        java.io.File elegido = selector.showOpenDialog(ventana);
        if (elegido == null) {
            return java.util.Optional.empty();
        }
        if (!Files.isRegularFile(elegido.toPath())) {
            Dialogos.error(ventana, Textos.t("componentes.ruta_no_valida"),
                    new ErrorAmistoso(Textos.t("componentes.no_existe"), null));
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(elegido.toPath());
    }

    @FXML
    private void abrirCarpetaHerramientas() {
        Path carpeta = contexto.binarios().carpetaHerramientas();
        if (!Files.isDirectory(carpeta)) {
            try {
                Files.createDirectories(carpeta);
            } catch (IOException e) {
                log.warn("No se ha podido crear {}: {}", carpeta, e.getMessage());
            }
        }
        Dialogos.abrirEnExplorador(carpeta, null);
    }

    @FXML
    private void cerrar() {
        if (ventana != null) {
            ventana.close();
        }
    }
}
