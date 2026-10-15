package com.ultimatedownloader;

import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.Idioma;
import com.ultimatedownloader.ui.Dialogos;
import com.ultimatedownloader.ui.OpcionesDesarrollo;
import com.ultimatedownloader.ui.ShellController;
import com.ultimatedownloader.ui.Ventanas;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Aplicación JavaFX: crea los servicios y monta la ventana. */
public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    /** Nombre de la aplicación. No se traduce: una marca es la misma en todos los idiomas. */
    public static final String NOMBRE = "Ultimate Downloader";

    private static final String RUTA_VISTA = "/com/ultimatedownloader/view/shell.fxml";

    private Contexto contexto;

    @Override
    public void init() {
        log.info("Iniciando {}", NOMBRE);
        contexto = new Contexto();
        // El idioma se fija antes de cargar ningún FXML: los textos entran al construir la vista.
        Textos.usar(Idioma.desdeCodigo(contexto.ajustes().getIdioma()));
    }

    @Override
    public void start(Stage ventana) throws IOException {
        Thread.setDefaultUncaughtExceptionHandler(this::errorInesperado);
        contexto.alRecargarInterfaz(() -> reconstruir(ventana));

        ventana.setTitle(NOMBRE);
        ventana.setMinWidth(1080);
        ventana.setMinHeight(720);
        ShellController armazon = montar(ventana);
        ventana.show();

        log.info("Ventana abierta ({}x{})",
                (int) ventana.getScene().getWidth(), (int) ventana.getScene().getHeight());
        new OpcionesDesarrollo(contexto, ventana.getScene(), armazon).aplicar();
    }

    /** Rehace la ventana desde el FXML, con el idioma que esté puesto. */
    private void reconstruir(Stage ventana) {
        try {
            montar(ventana);
            log.info("Interfaz reconstruida en {}", Textos.idioma().nombrePropio());
        } catch (IOException e) {
            log.error("No se ha podido rehacer la interfaz", e);
        }
    }

    /** Carga el armazón en la ventana. Si ya había escena, conserva su tamaño. */
    private ShellController montar(Stage ventana) throws IOException {
        FXMLLoader cargador = new FXMLLoader(App.class.getResource(RUTA_VISTA), Textos.paquete());
        cargador.setControllerFactory(this::crearControlador);
        Parent raiz = cargador.load();

        Scene anterior = ventana.getScene();
        ventana.setScene(anterior == null
                ? new Scene(raiz)
                : new Scene(raiz, anterior.getWidth(), anterior.getHeight()));
        Ventanas.preparar(ventana, contexto.ajustes().isTemaOscuro());

        ShellController armazon = cargador.getController();
        armazon.registrarAtajos();
        return armazon;
    }

    @Override
    public void stop() {
        log.info("Cerrando la aplicación");
        if (contexto != null) {
            contexto.cerrar();
        }
    }

    /** Crea cada controlador con el constructor que recibe el contexto, o con el vacío. */
    private Object crearControlador(Class<?> tipo) {
        try {
            try {
                return tipo.getDeclaredConstructor(Contexto.class).newInstance(contexto);
            } catch (NoSuchMethodException sinContexto) {
                return tipo.getDeclaredConstructor().newInstance();
            }
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                 | InvocationTargetException e) {
            throw new IllegalStateException("No se ha podido crear el controlador " + tipo, e);
        }
    }

    /** Último recurso: si algo se escapa, se registra y se explica con palabras. */
    private void errorInesperado(Thread hilo, Throwable causa) {
        log.error("Error inesperado en el hilo {}", hilo.getName(), causa);
        if (Platform.isFxApplicationThread()) {
            Dialogos.error(null, Textos.t("error.inesperado_titulo"), new ErrorAmistoso(
                    Textos.t("error.inesperado"),
                    Textos.t("error.inesperado_sugerencia"),
                    causa.toString()));
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
