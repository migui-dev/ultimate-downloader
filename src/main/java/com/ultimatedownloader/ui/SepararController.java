package com.ultimatedownloader.ui;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.fx.EnSegundoPlano;
import com.ultimatedownloader.model.separacion.Acelerador;
import com.ultimatedownloader.model.separacion.InfoGpu;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.separacion.DetectorGpu;
import com.ultimatedownloader.service.separacion.EntornoPython;
import com.ultimatedownloader.util.Formatos;
import com.ultimatedownloader.util.Textos;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Módulo Separar voz. Tiene dos caras: el asistente que monta el entorno de Python la primera
 * vez y el panel de trabajo cuando ya está listo. Aquí se lleva la primera y se decide cuál se
 * ve; del trabajo se encarga SepararTrabajoController.
 */
public final class SepararController {

    private static final Logger log = LoggerFactory.getLogger(SepararController.class);

    /** Lo que ocupa el entorno completo según el acelerador elegido, para avisar antes. */
    private static final long ESPACIO_CUDA = 5L << 30;
    private static final long ESPACIO_OTROS = 2L << 30;

    private final Contexto contexto;

    @FXML private ScrollPane panelInstalacion;
    @FXML private Node trabajo;
    @FXML private SepararTrabajoController trabajoController;

    @FXML private Label etiquetaGpu;
    @FXML private Label etiquetaQueSeInstala;
    @FXML private Label etiquetaEspacio;
    @FXML private Label etiquetaCarpetaEntorno;
    @FXML private ToggleGroup grupoAcelerador;
    @FXML private ToggleButton opcionCuda;
    @FXML private ToggleButton opcionDirectMl;
    @FXML private ToggleButton opcionCpu;
    @FXML private Label etiquetaAcelerador;
    @FXML private VBox cajaAvisoInstalacion;
    @FXML private Label etiquetaAvisoInstalacion;
    @FXML private VBox cajaProgreso;
    @FXML private Label etiquetaFase;
    @FXML private Label etiquetaPorcentaje;
    @FXML private ProgressBar barraInstalacion;
    @FXML private Label etiquetaDetalle;
    @FXML private Button botonInstalar;
    @FXML private Button botonCancelarInstalacion;

    private final AtomicBoolean cancelada = new AtomicBoolean();
    private Runnable alPedirConsola;
    private Runnable trasDetectar;
    private boolean gpuDetectada;
    private InfoGpu gpu;

    public SepararController(Contexto contexto) {
        this.contexto = contexto;
    }

    @FXML
    private void initialize() {
        etiquetaCarpetaEntorno.setText(entorno().carpeta().toString());
        prepararAceleradores();
        detectarGpu();
        if (trabajoController != null) {
            trabajoController.alBorrarMotor(() -> mostrarPanel(false));
            trabajoController.alPedirConsola(this::verConsola);
        }
        mostrarPanel(entorno().instalado());
        if (entorno().roto()) {
            mostrarAviso(cajaAvisoInstalacion, etiquetaAvisoInstalacion,
                    Textos.t("separar.entorno_roto"));
        }
    }

    /**
     * Lanza la instalación sin pulsar nada, para la opción -Pinstalar. Espera a que termine la
     * detección: si no, instalaría la versión para procesador en un equipo con NVIDIA.
     */
    public void instalarAlArrancar() {
        if (entorno().instalado()) {
            return;
        }
        if (gpuDetectada) {
            instalar();
        } else {
            trasDetectar = this::instalar;
        }
    }

    /** Carga un archivo en el panel de trabajo y lo manda a separar; opción de desarrollo. */
    public void separarAlArrancar(Path ruta) {
        if (trabajoController != null && entorno().instalado()) {
            trabajoController.separarAlArrancar(ruta);
        }
    }

    /** El armazón pasa aquí cómo abrir la consola, que es suya. */
    public void alPedirConsola(Runnable accion) {
        this.alPedirConsola = accion;
    }

    /** El armazón pasa aquí cómo llevar al usuario a la cola. */
    public void alIrALaCola(Runnable accion) {
        if (trabajoController != null) {
            trabajoController.alIrALaCola(accion);
        }
    }

    /** Vuelve a leer lo que haya cambiado en los ajustes. */
    public void refrescarTrasAjustes() {
        if (trabajoController != null) {
            trabajoController.refrescarTrasAjustes();
        }
    }

    // ------------------------------------------------------------------ estado

    private EntornoPython entorno() {
        return contexto.entornoPython();
    }

    private void mostrarPanel(boolean instalado) {
        alternar(trabajo, instalado);
        alternar(panelInstalacion, !instalado);
        if (instalado && trabajoController != null) {
            trabajoController.alMostrarse();
        }
    }

    private static void alternar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private void prepararAceleradores() {
        alternar(opcionDirectMl, Acelerador.DIRECTML.disponible());
        grupoAcelerador.selectedToggleProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                grupoAcelerador.selectToggle(antes);
                return;
            }
            refrescarAcelerador();
        });
    }

    /** Busca la tarjeta en segundo plano: nvidia-smi puede tardar un segundo. */
    private void detectarGpu() {
        etiquetaGpu.setText(Textos.t("comun.comprobando"));
        EnSegundoPlano.calcular(contexto.tareas(), DetectorGpu::nvidia,
                encontrada -> {
                    gpu = encontrada.orElse(null);
                    etiquetaGpu.setText(gpu != null ? gpu.resumen() : Textos.t("separar.sin_nvidia"));
                    seleccionar(gpu != null ? Acelerador.CUDA : Acelerador.CPU);
                    detectada();
                },
                causa -> {
                    log.debug("No se ha podido detectar la tarjeta gráfica");
                    etiquetaGpu.setText(Textos.t("comun.no_se_ha_podido_comprobar"));
                    seleccionar(Acelerador.CPU);
                    detectada();
                });
    }

    private void detectada() {
        gpuDetectada = true;
        if (trasDetectar != null) {
            Runnable accion = trasDetectar;
            trasDetectar = null;
            accion.run();
        }
    }

    private void seleccionar(Acelerador acelerador) {
        grupoAcelerador.selectToggle(switch (acelerador) {
            case CUDA -> opcionCuda;
            case DIRECTML -> Acelerador.DIRECTML.disponible() ? opcionDirectMl : opcionCpu;
            case CPU -> opcionCpu;
        });
        refrescarAcelerador();
    }

    private Acelerador aceleradorElegido() {
        if (grupoAcelerador.getSelectedToggle() == opcionCuda) {
            return Acelerador.CUDA;
        }
        if (grupoAcelerador.getSelectedToggle() == opcionDirectMl) {
            return Acelerador.DIRECTML;
        }
        return Acelerador.CPU;
    }

    /** Explica qué va a pasar con la opción elegida, y avisa si no encaja con el equipo. */
    private void refrescarAcelerador() {
        Acelerador acelerador = aceleradorElegido();
        etiquetaQueSeInstala.setText(switch (acelerador) {
            case CUDA -> Textos.t("separar.instalara_cuda", EntornoPython.VERSION_PYTHON);
            case DIRECTML -> Textos.t("separar.instalara_directml", EntornoPython.VERSION_PYTHON);
            case CPU -> Textos.t("separar.instalara_cpu", EntornoPython.VERSION_PYTHON);
        });
        etiquetaEspacio.setText(Textos.t("separar.unos_tanto", Formatos.tamano(
                acelerador == Acelerador.CUDA ? ESPACIO_CUDA : ESPACIO_OTROS, false)));
        etiquetaAcelerador.setText(switch (acelerador) {
            case CUDA -> Textos.t("separar.explicacion_cuda");
            case DIRECTML -> Textos.t("separar.explicacion_directml");
            case CPU -> Textos.t("separar.explicacion_cpu");
        });

        String aviso = null;
        if (entorno().roto()) {
            aviso = Textos.t("separar.entorno_roto");
        } else if (acelerador == Acelerador.CUDA && gpu == null) {
            aviso = Textos.t("separar.aviso_cuda_sin_tarjeta");
        } else if (acelerador == Acelerador.CPU && gpu != null) {
            aviso = Textos.t("separar.aviso_cpu_con_tarjeta", gpu.nombre());
        }
        mostrarAviso(cajaAvisoInstalacion, etiquetaAvisoInstalacion, aviso);
    }

    private static void mostrarAviso(VBox caja, Label etiqueta, String texto) {
        boolean hay = texto != null && !texto.isBlank();
        etiqueta.setText(hay ? texto : "");
        alternar(caja, hay);
    }

    // ------------------------------------------------------------------ instalación

    @FXML
    private void instalar() {
        Acelerador acelerador = aceleradorElegido();
        cancelada.set(false);
        enMarcha(true);

        EnSegundoPlano.hacer(contexto.tareas(),
                () -> entorno().instalar(acelerador,
                        (fase, detalle, progreso) ->
                                Platform.runLater(() -> avanzar(fase, detalle, progreso)),
                        cancelada::get),
                () -> {
                    enMarcha(false);
                    alternar(cajaProgreso, true);
                    etiquetaFase.setText(Textos.t("separar.motor_instalado"));
                    mostrarPanel(true);
                },
                causa -> {
                    enMarcha(false);
                    alternar(cajaProgreso, true);
                    etiquetaFase.setText(cancelada.get()
                            ? Textos.t("separar.instalacion_cancelada")
                            : Textos.t("separar.no_se_ha_podido_instalar"));
                    if (!cancelada.get() && causa instanceof AppException fallo) {
                        Dialogos.error(ventana(), Textos.t("shell.separar_voz"), fallo.error());
                    }
                });
    }

    private void avanzar(String fase, String detalle, double progreso) {
        alternar(cajaProgreso, true);
        if (fase != null) {
            etiquetaFase.setText(fase);
        }
        if (detalle != null) {
            etiquetaDetalle.setText(detalle);
        }
        // Un progreso negativo significa «esto va a tardar y no se sabe cuánto»: la barra pasa
        // a indeterminada en lugar de quedarse clavada en un número que no cambia.
        barraInstalacion.setProgress(progreso >= 0 ? progreso : ProgressBar.INDETERMINATE_PROGRESS);
        etiquetaPorcentaje.setText(progreso >= 0 ? Formatos.porcentaje(progreso) : "");
    }

    private void enMarcha(boolean activa) {
        botonInstalar.setDisable(activa);
        alternar(cajaProgreso, activa);
        alternar(botonCancelarInstalacion, activa);
        alternar(barraInstalacion, activa);
        alternar(etiquetaDetalle, activa);
        opcionCuda.setDisable(activa);
        opcionDirectMl.setDisable(activa);
        opcionCpu.setDisable(activa);
        if (!activa) {
            etiquetaDetalle.setText("");
            etiquetaPorcentaje.setText("");
        }
    }

    @FXML
    private void cancelarInstalacion() {
        cancelada.set(true);
        etiquetaFase.setText(Textos.t("comun.cancelando"));
        entorno().cancelar();
    }

    @FXML
    private void verConsola() {
        if (alPedirConsola != null) {
            alPedirConsola.run();
        }
    }

    private Window ventana() {
        return panelInstalacion.getScene() != null
                ? panelInstalacion.getScene().getWindow()
                : null;
    }
}
