package com.ultimatedownloader;

import com.ultimatedownloader.model.DownloadJob;
import com.ultimatedownloader.model.DownloadRequest;
import com.ultimatedownloader.model.Settings;
import com.ultimatedownloader.model.convert.ConversionJob;
import com.ultimatedownloader.model.convert.ConversionRequest;
import com.ultimatedownloader.model.separacion.SeparacionJob;
import com.ultimatedownloader.model.separacion.SeparacionRequest;
import com.ultimatedownloader.service.AppPaths;
import com.ultimatedownloader.service.BinaryManager;
import com.ultimatedownloader.service.Plataforma;
import com.ultimatedownloader.service.SettingsService;
import com.ultimatedownloader.service.YtDlpService;
import com.ultimatedownloader.service.cola.ColaService;
import com.ultimatedownloader.service.consola.Consola;
import com.ultimatedownloader.service.convert.EjecutorConversion;
import com.ultimatedownloader.service.convert.MediaProbe;
import com.ultimatedownloader.service.descarga.EjecutorDescarga;
import com.ultimatedownloader.service.separacion.EjecutorSeparacion;
import com.ultimatedownloader.service.separacion.EntornoPython;
import com.ultimatedownloader.service.separacion.ModelosService;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Servicios compartidos, creados una sola vez al arrancar. Ninguno toca la interfaz. */
public final class Contexto {

    private static final Logger log = LoggerFactory.getLogger(Contexto.class);

    private final SettingsService ajustes = new SettingsService();
    private final Consola consola = new Consola(Platform::runLater);
    private final BinaryManager binarios = new BinaryManager();
    private final ExecutorService tareas = Executors.newFixedThreadPool(3, tarea -> {
        Thread hilo = new Thread(tarea, "tarea-app");
        hilo.setDaemon(true);
        return hilo;
    });

    private Path ytDlp;
    private Path ffmpeg;
    private YtDlpService ytDlpService;
    private ColaService cola;
    private EjecutorDescarga ejecutorDescarga;
    private EjecutorConversion ejecutorConversion;
    private MediaProbe probe;
    private Runnable recargarInterfaz = () -> { };
    private EntornoPython entornoPython;
    private ModelosService modelos;
    private EjecutorSeparacion ejecutorSeparacion;

    public Contexto() {
        localizarBinarios();
        cola = new ColaService(ajustes.ajustes().getDescargasSimultaneas(),
                Platform::runLater, consola);
        // -Dud.entorno=<carpeta> permite revisar el asistente de instalación sin tener que
        // borrar el entorno de verdad.
        String carpetaEntorno = System.getProperty("ud.entorno");
        entornoPython = carpetaEntorno != null && !carpetaEntorno.isBlank()
                ? new EntornoPython(Path.of(carpetaEntorno), consola)
                : new EntornoPython(consola);
        modelos = new ModelosService(entornoPython);
        ejecutorSeparacion = new EjecutorSeparacion(entornoPython, ffmpeg);
    }

    /** Vuelve a buscar yt-dlp y ffmpeg y reconstruye el servicio que los usa. */
    public void localizarBinarios() {
        Settings s = ajustes.ajustes();
        ytDlp = binarios.localizarYtDlp(s.getRutaYtDlp()).orElse(null);
        ffmpeg = binarios.localizarFfmpeg(s.getRutaFfmpeg()).orElse(null);
        ytDlpService = new YtDlpService(
                ytDlp != null ? ytDlp
                        : binarios.carpetaHerramientas().resolve(Plataforma.ejecutable("yt-dlp")),
                ffmpeg);
        ejecutorDescarga = new EjecutorDescarga(ytDlpService);
        if (ffmpeg != null) {
            ejecutorConversion = new EjecutorConversion(ffmpeg);
            probe = new MediaProbe(ffmpeg.resolveSibling(Plataforma.ejecutable("ffprobe")));
        }
        if (entornoPython != null) {
            // El ejecutor guarda la ruta de ffmpeg, así que se rehace si esa ruta cambia.
            ejecutorSeparacion = new EjecutorSeparacion(entornoPython, ffmpeg);
        }
        log.info("Binarios: yt-dlp={}, ffmpeg={}", ytDlp, ffmpeg);
    }

    /** Lo pone App. Las tareas viven aquí, así que rehacer las pantallas no interrumpe nada. */
    public void alRecargarInterfaz(Runnable accion) {
        this.recargarInterfaz = accion != null ? accion : () -> { };
    }

    public void recargarInterfaz() {
        recargarInterfaz.run();
    }

    /** Registro común de la salida de los procesos externos. */
    public Consola consola() {
        return consola;
    }

    public SettingsService servicioAjustes() {
        return ajustes;
    }

    public Settings ajustes() {
        return ajustes.ajustes();
    }

    public BinaryManager binarios() {
        return binarios;
    }

    public YtDlpService ytDlp() {
        return ytDlpService;
    }

    public ColaService cola() {
        return cola;
    }

    /** Catálogo de modelos de separación. */
    public ModelosService modelos() {
        return modelos;
    }

    /** Encola una separación con el ejecutor que le corresponde. */
    public SeparacionJob encolarSeparacion(SeparacionRequest peticion) {
        SeparacionJob trabajo = new SeparacionJob(cola.siguienteId(), peticion);
        cola.encolar(trabajo, ejecutorSeparacion);
        return trabajo;
    }

    /** Entorno de Python propio donde vive el motor de separación. */
    public EntornoPython entornoPython() {
        return entornoPython;
    }

    /** Lector de metadatos con ffprobe; null si no hay ffmpeg. */
    public MediaProbe probe() {
        return probe;
    }

    /** Encola una conversión con el ejecutor que le corresponde. */
    public ConversionJob encolarConversion(ConversionRequest peticion) {
        ConversionJob trabajo = new ConversionJob(cola.siguienteId(), peticion);
        cola.encolar(trabajo, ejecutorConversion);
        return trabajo;
    }

    /** Encola una descarga con el ejecutor que le corresponde. */
    public DownloadJob encolarDescarga(DownloadRequest peticion) {
        DownloadJob trabajo = new DownloadJob(cola.siguienteId(), peticion);
        cola.encolar(trabajo, ejecutorDescarga);
        return trabajo;
    }

    public ExecutorService tareas() {
        return tareas;
    }

    public Optional<Path> rutaYtDlp() {
        return Optional.ofNullable(ytDlp);
    }

    public Optional<Path> rutaFfmpeg() {
        return Optional.ofNullable(ffmpeg);
    }

    /** true si están los dos binarios y se puede descargar con normalidad. */
    public boolean binariosListos() {
        return ytDlp != null && ffmpeg != null;
    }

    /** Carpeta de destino guardada en los ajustes, o la de Descargas la primera vez. */
    public Path carpetaDestino() {
        String guardada = ajustes.ajustes().getCarpetaDestino();
        if (guardada != null && !guardada.isBlank()) {
            try {
                return Path.of(guardada);
            } catch (InvalidPathException e) {
                log.warn("La carpeta guardada «{}» no es una ruta válida", guardada);
            }
        }
        return AppPaths.carpetaDescargasPorDefecto();
    }

    /** Guarda la carpeta como la última usada y la añade a las recientes. */
    public void recordarCarpeta(Path carpeta) {
        if (carpeta == null) {
            return;
        }
        Settings s = ajustes.ajustes();
        s.setCarpetaDestino(carpeta.toString());
        s.registrarCarpetaReciente(carpeta.toString());
        ajustes.guardar();
    }

    /** Cierra la cola y los hilos; se llama al cerrar la ventana. */
    public void cerrar() {
        ajustes.guardar();
        cola.cerrar();
        tareas.shutdownNow();
    }
}
