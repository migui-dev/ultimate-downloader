package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.fx.EnSegundoPlano;
import com.ultimatedownloader.model.AudioOutputFormat;
import com.ultimatedownloader.model.CookieBrowser;
import com.ultimatedownloader.model.DownloadMode;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.Idioma;
import com.ultimatedownloader.model.Settings;
import com.ultimatedownloader.model.VideoContainer;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.AppPaths;
import com.ultimatedownloader.service.FilenameTemplate;
import com.ultimatedownloader.service.FolderValidator;
import com.ultimatedownloader.ui.iconos.Iconos;
import com.ultimatedownloader.ui.iconos.Lucide;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pantalla de ajustes. Cada cambio se guarda en cuanto se hace: no hay botón de «Aceptar» que
 * se pueda olvidar.
 */
public final class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final Contexto contexto;
    private final ToggleGroup grupoTema = new ToggleGroup();

    @FXML private Label etiquetaCarpeta;
    @FXML private Spinner<Integer> selectorSimultaneas;
    @FXML private Spinner<Integer> selectorFragmentos;
    @FXML private TextField campoPlantilla;
    @FXML private MenuButton menuMarcadores;
    @FXML private ComboBox<DownloadMode> comboModo;
    @FXML private ComboBox<VideoContainer> comboContenedor;
    @FXML private ComboBox<AudioOutputFormat> comboFormatoAudio;
    @FXML private CheckBox casillaAvisar;
    @FXML private ToggleButton temaOscuro;
    @FXML private ToggleButton temaClaro;
    @FXML private ComboBox<Idioma> comboIdioma;
    @FXML private Label etiquetaIdioma;
    @FXML private ComboBox<CookieBrowser> comboCookies;
    @FXML private TextField campoPerfilCookies;
    @FXML private Label etiquetaPerfilCookies;
    @FXML private Button botonProbarCookies;
    @FXML private Label etiquetaCookies;
    @FXML private Label etiquetaYtDlp;
    @FXML private Label etiquetaFfmpeg;
    @FXML private Button botonActualizar;
    @FXML private Label etiquetaActualizacion;
    @FXML private Label etiquetaRutaAjustes;

    private Stage ventana;
    private boolean cambioDeIdioma;
    private Runnable alCerrar = () -> { };

    public SettingsController(Contexto contexto) {
        this.contexto = contexto;
    }

    /** Abre los ajustes como ventana modal. Devuelve true si hay que rehacer la interfaz. */
    public static boolean mostrar(Window propietario, Contexto contexto, Runnable alCerrar) {
        try {
            FXMLLoader cargador = new FXMLLoader(
                    SettingsController.class.getResource("/com/ultimatedownloader/view/settings.fxml"),
                    Textos.paquete());
            cargador.setControllerFactory(tipo -> new SettingsController(contexto));
            Scene escena = new Scene(cargador.load());

            Stage ventana = new Stage();
            ventana.initOwner(propietario);
            ventana.initModality(Modality.APPLICATION_MODAL);
            ventana.setTitle(Textos.t("settings.ajustes"));
            ventana.setScene(escena);
            ventana.setMinWidth(680);
            ventana.setMinHeight(520);
            Ventanas.preparar(ventana, contexto.ajustes().isTemaOscuro());

            SettingsController controlador = cargador.getController();
            controlador.ventana = ventana;
            controlador.alCerrar = alCerrar != null ? alCerrar : () -> { };
            ventana.showAndWait();
            return controlador.cambioDeIdioma;
        } catch (IOException e) {
            log.error("No se han podido abrir los ajustes", e);
            Dialogos.error(propietario, Textos.t("settings.ajustes"), new ErrorAmistoso(
                    Textos.t("settings.no_se_ha_podido_abrir"), null, e.toString()));
            return false;
        }
    }

    @FXML
    private void initialize() {
        Settings s = contexto.ajustes();

        etiquetaRutaAjustes.setText(Textos.t("settings.se_guardan_en",
                contexto.servicioAjustes().fichero()));
        refrescarCarpeta();

        numero(selectorSimultaneas, 1, 8, s.getDescargasSimultaneas(), cuantas -> {
            s.setDescargasSimultaneas(cuantas);
            contexto.cola().ajustarSimultaneas(cuantas);
        });
        numero(selectorFragmentos, 1, 16, s.getFragmentosSimultaneos(),
                s::setFragmentosSimultaneos);

        texto(campoPlantilla, s.getPlantillaNombre(), s::setPlantillaNombre);
        prepararMarcadores();

        lista(comboModo, DownloadMode.values(), s.getModoPorDefecto(), s::setModoPorDefecto);
        lista(comboContenedor, VideoContainer.values(), s.getContenedorPorDefecto(),
                s::setContenedorPorDefecto);
        lista(comboFormatoAudio, AudioOutputFormat.values(), s.getFormatoAudioPorDefecto(),
                s::setFormatoAudioPorDefecto);
        casilla(casillaAvisar, s.isAvisarIncompatibilidad(), s::setAvisarIncompatibilidad);

        prepararTema(s);
        prepararIdioma(s);

        lista(comboCookies, CookieBrowser.values(), s.getNavegadorCookies(),
                s::setNavegadorCookies);
        comboCookies.valueProperty().addListener((obs, antes, ahora) -> prepararCookies());
        prepararCookies();
        texto(campoPerfilCookies, s.getRutaPerfilCookies(), ruta ->
                s.setRutaPerfilCookies(ruta == null || ruta.isBlank() ? null : ruta.strip()));

        refrescarComponentes();
    }

    // ------------------------------------------------------------------ enlaces con los ajustes

    /**
     * Cada control se enlaza con su ajuste igual: se le pone el valor guardado y, a partir de
     * ahí, cada cambio se escribe en el fichero. No hay botón de «Aceptar» que se pueda olvidar.
     */
    private <T> void lista(ComboBox<T> combo, T[] opciones, T valor, Consumer<T> guardar) {
        combo.getItems().setAll(opciones);
        combo.setValue(valor);
        combo.valueProperty().addListener((obs, antes, ahora) -> anotar(guardar, ahora));
    }

    private void numero(Spinner<Integer> selector, int minimo, int maximo, int valor,
                        Consumer<Integer> guardar) {
        selector.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(minimo, maximo, valor));
        selector.valueProperty().addListener((obs, antes, ahora) -> anotar(guardar, ahora));
    }

    private void texto(TextField campo, String valor, Consumer<String> guardar) {
        campo.setText(valor);
        campo.textProperty().addListener((obs, antes, ahora) -> anotar(guardar, ahora));
    }

    private void casilla(CheckBox casilla, boolean valor, Consumer<Boolean> guardar) {
        casilla.setSelected(valor);
        casilla.selectedProperty().addListener((obs, antes, ahora) -> anotar(guardar, ahora));
    }

    private <T> void anotar(Consumer<T> guardar, T valor) {
        guardar.accept(valor);
        guardar();
    }

    private void prepararMarcadores() {
        menuMarcadores.setGraphic(Iconos.de(Lucide.ANADIR));
        FilenameTemplate.marcadores().forEach((marcador, descripcion) -> {
            MenuItem elemento = new MenuItem(marcador + "  ·  " + descripcion);
            elemento.setOnAction(e -> campoPlantilla.setText(campoPlantilla.getText() + marcador));
            menuMarcadores.getItems().add(elemento);
        });
    }

    private void prepararTema(Settings s) {
        temaOscuro.setToggleGroup(grupoTema);
        temaClaro.setToggleGroup(grupoTema);
        grupoTema.selectToggle(s.isTemaOscuro() ? temaOscuro : temaClaro);
        grupoTema.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoTema.selectToggle(antes);
                return;
            }
            boolean oscuro = ahora == temaOscuro;
            s.setTemaOscuro(oscuro);
            guardar();
            Ventanas.aplicarTemaATodas(oscuro);
        });
    }

    /**
     * Desplegable de idiomas. Al cambiarlo se reconstruye la interfaz entera: las tareas viven en
     * el contexto, no en las pantallas, así que no se pierde nada de lo que esté en marcha.
     */
    private void prepararIdioma(Settings s) {
        comboIdioma.getItems().add(Idioma.SISTEMA);
        comboIdioma.getItems().addAll(Idioma.traducidos());
        comboIdioma.setConverter(Controles.comoTexto(idioma ->
                idioma == null || idioma == Idioma.SISTEMA
                        ? Textos.t("settings.idioma_del_sistema")
                        : idioma.nombrePropio()));
        comboIdioma.setValue(Idioma.desdeCodigo(s.getIdioma()));
        etiquetaIdioma.setText(Textos.t("settings.idioma_se_aplica_al_momento"));

        comboIdioma.valueProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null || ahora == antes) {
                return;
            }
            s.setIdioma(ahora == Idioma.SISTEMA ? "sistema" : ahora.codigo());
            guardar();
            Textos.usar(ahora);
            cambioDeIdioma = true;
            // La propia ventana de ajustes también está en el idioma anterior: se cierra y el
            // armazón la vuelve a abrir ya traducida.
            ventana.close();
        });
    }

    private void refrescarCarpeta() {
        etiquetaCarpeta.setText(contexto.carpetaDestino().toString());
    }

    private void refrescarComponentes() {
        etiquetaYtDlp.setText(contexto.rutaYtDlp().map(Path::toString)
                .orElse(Textos.t("comun.no_encontrado")));
        etiquetaFfmpeg.setText(contexto.rutaFfmpeg().map(Path::toString)
                .orElse(Textos.t("comun.no_encontrado")));
        botonActualizar.setDisable(contexto.rutaYtDlp().isEmpty());
        consultarVersionYtDlp();
    }

    /**
     * La versión de yt-dlp se consulta aquí y no en la barra lateral: es un dato de mantenimiento,
     * interesa cuando se viene a actualizarlo.
     */
    private void consultarVersionYtDlp() {
        if (contexto.rutaYtDlp().isEmpty()) {
            return;
        }
        String ruta = etiquetaYtDlp.getText();
        EnSegundoPlano.calcular(contexto.tareas(), () -> contexto.ytDlp().version(),
                version -> etiquetaYtDlp.setText(ruta + "  ·  " + version),
                causa -> log.debug("No se ha podido consultar la versión de yt-dlp"));
    }

    private void guardar() {
        contexto.servicioAjustes().guardar();
    }

    // ------------------------------------------------------------------ acciones

    @FXML
    private void cambiarCarpeta() {
        DirectoryChooser selector = new DirectoryChooser();
        selector.setTitle(Textos.t("settings.elige_carpeta_por_defecto"));
        Path actual = contexto.carpetaDestino();
        if (Files.isDirectory(actual)) {
            selector.setInitialDirectory(actual.toFile());
        }
        java.io.File elegida = selector.showDialog(ventana);
        if (elegida == null) {
            return;
        }
        FolderValidator.Resultado resultado = FolderValidator.validar(elegida.toPath(), 0);
        if (!resultado.valida()) {
            Dialogos.error(ventana, Textos.t("comun.carpeta_no_valida"),
                    new ErrorAmistoso(resultado.mensaje(), Textos.t("comun.elige_otra_carpeta")));
            return;
        }
        contexto.recordarCarpeta(elegida.toPath());
        refrescarCarpeta();
    }

    /**
     * El campo de al lado vale para dos cosas según de dónde salgan las cookies: la carpeta
     * del perfil de un navegador o la ruta de un archivo cookies.txt.
     */
    private void prepararCookies() {
        boolean archivo = comboCookies.getValue() != null && comboCookies.getValue().usaArchivo();
        etiquetaPerfilCookies.setText(Textos.t(archivo
                ? "settings.archivo_de_cookies"
                : "settings.carpeta_del_perfil_opcional"));
        boolean activas = comboCookies.getValue() != null && comboCookies.getValue().activo();
        campoPerfilCookies.setDisable(!activas);
        botonProbarCookies.setDisable(!activas);
        etiquetaCookies.setText("");
    }

    @FXML
    private void elegirPerfilCookies() {
        CookieBrowser fuente = comboCookies.getValue();
        java.io.File elegida;
        if (fuente != null && fuente.usaArchivo()) {
            FileChooser selector = new FileChooser();
            selector.setTitle(Textos.t("settings.elige_el_cookies_txt"));
            selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "cookies.txt", "*.txt"));
            elegida = selector.showOpenDialog(ventana);
        } else {
            DirectoryChooser selector = new DirectoryChooser();
            selector.setTitle(Textos.t("settings.elige_perfil_navegador"));
            elegida = selector.showDialog(ventana);
        }
        if (elegida != null) {
            campoPerfilCookies.setText(elegida.toString());
        }
    }

    /**
     * Prueba las cookies de verdad, con una petición a YouTube, y dice qué pasa.
     *
     * Es la única forma de saberlo: que el navegador esté elegido no significa que yt-dlp
     * pueda leer sus cookies, y antes eso solo se descubría cuando fallaba una descarga.
     */
    @FXML
    private void probarCookies() {
        botonProbarCookies.setDisable(true);
        etiquetaCookies.getStyleClass().setAll("texto-3");
        etiquetaCookies.setText(Textos.t("settings.comprobando_cookies"));

        CookieBrowser fuente = comboCookies.getValue();
        String ruta = campoPerfilCookies.getText();
        EnSegundoPlano.calcular(contexto.tareas(),
                () -> contexto.ytDlp().comprobarCookies(fuente, ruta),
                resultado -> {
                    botonProbarCookies.setDisable(false);
                    etiquetaCookies.setText(resultado.mensaje());
                    etiquetaCookies.getStyleClass().setAll(
                            resultado.correcto() ? "mensaje-exito" : "mensaje-aviso");
                },
                causa -> {
                    botonProbarCookies.setDisable(false);
                    etiquetaCookies.getStyleClass().setAll("mensaje-aviso");
                    etiquetaCookies.setText(Textos.t("comun.no_se_ha_podido_comprobar"));
                });
    }

    @FXML
    private void actualizarYtDlp() {
        botonActualizar.setDisable(true);
        etiquetaActualizacion.setText(Textos.t("settings.actualizando_ytdlp"));

        EnSegundoPlano.calcular(contexto.tareas(), () -> contexto.ytDlp().actualizar(),
                salida -> {
                    botonActualizar.setDisable(false);
                    etiquetaActualizacion.setText(resumirActualizacion(salida));
                    log.info("yt-dlp actualizado: {}", salida);
                },
                causa -> {
                    botonActualizar.setDisable(false);
                    etiquetaActualizacion.setText("");
                    Dialogos.error(ventana, Textos.t("settings.actualizar_yt_dlp"),
                            causa instanceof AppException app
                                    ? app.error()
                                    : new ErrorAmistoso(
                                            Textos.t("settings.no_se_ha_podido_actualizar"),
                                            Textos.t("settings.comprueba_conexion"),
                                            String.valueOf(causa)));
                });
    }

    /** yt-dlp contesta con varias líneas; se muestra la que de verdad informa. */
    static String resumirActualizacion(String salida) {
        if (salida == null || salida.isBlank()) {
            return Textos.t("settings.ya_estaba_al_dia");
        }
        for (String linea : salida.split("\\R")) {
            String l = linea.strip();
            if (l.toLowerCase(java.util.Locale.ROOT).contains("up to date")) {
                return Textos.t("settings.ya_esta_al_dia");
            }
            if (l.toLowerCase(java.util.Locale.ROOT).contains("updated")
                    || l.toLowerCase(java.util.Locale.ROOT).contains("updating")) {
                return Textos.t("settings.actualizado", l);
            }
        }
        return salida.lines().reduce((primera, ultima) -> ultima).orElse("").strip();
    }

    @FXML
    private void abrirAsistente() {
        SetupController.mostrar(ventana, contexto);
        refrescarComponentes();
    }

    @FXML
    private void abrirRegistro() {
        Path carpeta = AppPaths.carpetaLogs();
        if (!Files.isDirectory(carpeta)) {
            Dialogos.informacion(ventana, Textos.t("settings.registro"),
                    Textos.t("settings.no_hay_registro"),
                    Textos.t("settings.se_creara_en", carpeta));
            return;
        }
        Dialogos.abrirEnExplorador(carpeta, null);
    }

    @FXML
    private void cerrar() {
        guardar();
        alCerrar.run();
        if (ventana != null) {
            ventana.close();
        }
    }
}
