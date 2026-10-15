package com.ultimatedownloader.service.cola;

import com.ultimatedownloader.model.DownloadPhase;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.JobStatus;
import com.ultimatedownloader.model.ProgressSnapshot;
import com.ultimatedownloader.model.Tarea;
import com.ultimatedownloader.model.TipoTarea;
import com.ultimatedownloader.service.ErrorTranslator;
import com.ultimatedownloader.service.ProcessRunner;
import com.ultimatedownloader.service.consola.Consola;
import com.ultimatedownloader.util.Textos;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cola común: descargas, conversiones y separaciones. Lleva los turnos, cancela matando el
 * proceso y sus hijos, limpia los temporales y repite una vez los fallos pasajeros. Lo propio
 * de cada tipo de trabajo vive en su EjecutorTarea.
 *
 * El trabajo pesado va en un ExecutorService y las propiedades de cada tarea se tocan a través
 * del despachador del constructor. Los tests le pasan uno que ejecuta en el mismo hilo.
 */
public final class ColaService {

    private static final Logger log = LoggerFactory.getLogger(ColaService.class);

    /** Veces que se repite sola una tarea cuando el fallo parece pasajero. */
    private static final int REINTENTOS_AUTOMATICOS = 1;
    private static final long ESPERA_ENTRE_INTENTOS_MS = 4000;

    /**
     * Lista con extractor: además de las altas y bajas, avisa cuando cambia el estado de una
     * tarea, para que el resumen y el contador de la barra lateral se refresquen solos.
     */
    private final ObservableList<Tarea> tareas = FXCollections.observableArrayList(
            tarea -> new Observable[]{tarea.estadoProperty()});

    private final Map<Integer, ProcessRunner.ProcesoEnCurso> procesos = new ConcurrentHashMap<>();
    private final Map<Integer, EjecutorTarea> ejecutores = new ConcurrentHashMap<>();
    private final AtomicInteger siguienteId = new AtomicInteger(1);
    private final SemaforoAjustable permisos;
    private final ExecutorService ejecutor;
    private final Consumer<Runnable> despachador;
    private final Consola consola;

    private volatile int simultaneas;

    public ColaService(int simultaneas, Consumer<Runnable> despachador, Consola consola) {
        this.simultaneas = Math.max(1, simultaneas);
        this.permisos = new SemaforoAjustable(this.simultaneas);
        this.despachador = despachador;
        this.consola = consola;
        this.ejecutor = Executors.newCachedThreadPool(tarea -> {
            Thread hilo = new Thread(tarea, "cola");
            hilo.setDaemon(true);
            return hilo;
        });
    }

    public ObservableList<Tarea> tareas() {
        return tareas;
    }

    public int simultaneas() {
        return simultaneas;
    }

    /** Siguiente identificador libre; lo usan los módulos al crear sus tareas. */
    public int siguienteId() {
        return siguienteId.getAndIncrement();
    }

    /** Añade una tarea a la cola y la pone en marcha cuando haya hueco. */
    public void encolar(Tarea tarea, EjecutorTarea ejecutorDeTarea) {
        ejecutores.put(tarea.id(), ejecutorDeTarea);
        enInterfaz(() -> tareas.add(tarea));
        ejecutor.execute(() -> procesar(tarea));
        log.info("Encolada {} {} en {}", tarea.tipo(), tarea.id(), tarea.carpetaDestino());
    }

    /** Cancela una tarea: mata el proceso y borra los temporales. */
    public void cancelar(Tarea tarea) {
        if (tarea == null || !tarea.estado().sePuedeCancelar()) {
            return;
        }
        enInterfaz(() -> {
            tarea.estadoProperty().set(JobStatus.CANCELADA);
            tarea.mensajeProperty().set(Textos.t("cola.estado_cancelada"));
            tarea.progresoProperty().set(-1);
            tarea.velocidadBytesPorSegProperty().set(0);
            tarea.segundosRestantesProperty().set(null);
        });
        ProcessRunner.ProcesoEnCurso proceso = procesos.get(tarea.id());
        if (proceso != null) {
            proceso.cancelar();
        }
        EjecutorTarea suyo = ejecutores.get(tarea.id());
        if (suyo != null) {
            suyo.limpiar(tarea);
        }
    }

    /** Vuelve a intentar una tarea que falló o se canceló. */
    public void reintentar(Tarea tarea) {
        if (tarea == null || !tarea.estado().sePuedeReintentar()) {
            return;
        }
        EjecutorTarea suyo = ejecutores.get(tarea.id());
        if (suyo == null) {
            return;
        }
        suyo.prepararReintento(tarea);
        enInterfaz(() -> {
            tarea.estadoProperty().set(JobStatus.EN_COLA);
            tarea.faseProperty().set(DownloadPhase.EN_COLA);
            tarea.progresoProperty().set(-1);
            tarea.mensajeProperty().set("");
            tarea.sugerenciaProperty().set(null);
            tarea.bytesDescargadosProperty().set(0);
        });
        ejecutor.execute(() -> procesar(tarea));
    }

    /** Quita todas las tareas terminadas de la lista. */
    public void limpiarTerminadas() {
        enInterfaz(() -> tareas.removeIf(t -> {
            if (t.estado().terminada()) {
                ejecutores.remove(t.id());
                return true;
            }
            return false;
        }));
    }

    /** Cambia el número de tareas simultáneas sin reiniciar nada. */
    public void ajustarSimultaneas(int nuevas) {
        int valor = Math.clamp(nuevas, 1, 8);
        int diferencia = valor - simultaneas;
        if (diferencia > 0) {
            permisos.release(diferencia);
        } else if (diferencia < 0) {
            permisos.reducir(-diferencia);
        }
        simultaneas = valor;
        log.info("Tareas simultáneas: {}", valor);
    }

    /** Cancela todo y cierra los hilos. */
    public void cerrar() {
        new ArrayList<>(tareas).forEach(this::cancelar);
        ejecutor.shutdownNow();
        try {
            if (!ejecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Quedan hilos de la cola sin terminar");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ ejecución

    private void procesar(Tarea tarea) {
        try {
            permisos.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            if (tarea.estado() == JobStatus.CANCELADA) {
                return;
            }
            ejecutarConReintentos(tarea);
        } catch (RuntimeException e) {
            log.error("Fallo inesperado en la tarea {}", tarea.id(), e);
            fallar(tarea, ErrorTranslator.traducir(e));
        } finally {
            procesos.remove(tarea.id());
            permisos.release();
        }
    }

    /**
     * Ejecuta la tarea y, si el fallo parece pasajero, la repite una vez. YouTube devuelve a veces
     * un 403 al pedirle dos descargas seguidas del mismo vídeo y basta con esperar unos segundos.
     */
    private void ejecutarConReintentos(Tarea tarea) {
        EjecutorTarea suyo = ejecutores.get(tarea.id());
        if (suyo == null) {
            log.error("La tarea {} no tiene ejecutor", tarea.id());
            return;
        }

        for (int intento = 0; intento <= REINTENTOS_AUTOMATICOS; intento++) {
            enInterfaz(() -> {
                tarea.estadoProperty().set(JobStatus.EN_CURSO);
                tarea.faseProperty().set(DownloadPhase.PREPARANDO);
                tarea.mensajeProperty().set("");
            });

            ResultadoTarea resultado;
            try {
                resultado = suyo.ejecutar(tarea, new Seguimiento(tarea));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                suyo.limpiar(tarea);
                return;
            }

            if (resultado.cancelada() || tarea.estado() == JobStatus.CANCELADA) {
                suyo.limpiar(tarea);
                log.info("Tarea {} cancelada", tarea.id());
                return;
            }

            if (resultado.exito()) {
                enInterfaz(() -> {
                    tarea.estadoProperty().set(JobStatus.COMPLETADA);
                    tarea.faseProperty().set(DownloadPhase.TERMINADA);
                    tarea.progresoProperty().set(1);
                    tarea.mensajeProperty().set(Textos.t("cola.estado_completada"));
                    tarea.velocidadBytesPorSegProperty().set(0);
                    tarea.segundosRestantesProperty().set(null);
                });
                log.info("Tarea {} completada: {}", tarea.id(), tarea.archivoFinal());
                return;
            }

            suyo.limpiar(tarea);

            if (intento < REINTENTOS_AUTOMATICOS && resultado.pasajero()) {
                log.info("El fallo de la tarea {} parece pasajero: se reintenta", tarea.id());
                enInterfaz(() -> {
                    tarea.faseProperty().set(DownloadPhase.PREPARANDO);
                    tarea.mensajeProperty().set(Textos.t("cola.estado_reintentando"));
                    tarea.progresoProperty().set(-1);
                });
                try {
                    Thread.sleep(ESPERA_ENTRE_INTENTOS_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }

            fallar(tarea, resultado.error());
            return;
        }
    }

    private void fallar(Tarea tarea, ErrorAmistoso error) {
        ErrorAmistoso mostrado = error != null
                ? error
                : new ErrorAmistoso(Textos.t("cola.ha_fallado"), Textos.t("comun.consulta_la_consola"));
        enInterfaz(() -> {
            tarea.estadoProperty().set(JobStatus.ERROR);
            tarea.progresoProperty().set(-1);
            tarea.velocidadBytesPorSegProperty().set(0);
            tarea.segundosRestantesProperty().set(null);
            tarea.mensajeProperty().set(mostrado.mensaje());
            tarea.sugerenciaProperty().set(mostrado.sugerencia());
        });
    }

    private void enInterfaz(Runnable accion) {
        despachador.accept(accion);
    }

    /** Puente entre un ejecutor y la cola. */
    private final class Seguimiento implements SeguimientoTarea {

        private final Tarea tarea;

        Seguimiento(Tarea tarea) {
            this.tarea = tarea;
        }

        @Override
        public void registrarProceso(ProcessRunner.ProcesoEnCurso proceso) {
            procesos.put(tarea.id(), proceso);
        }

        @Override
        public void fase(DownloadPhase fase) {
            enInterfaz(() -> tarea.faseProperty().set(fase));
        }

        @Override
        public void progreso(ProgressSnapshot instantanea) {
            enInterfaz(() -> {
                tarea.faseProperty().set(instantanea.fase());
                tarea.progresoProperty().set(instantanea.fraccion());
                tarea.bytesDescargadosProperty().set(instantanea.bytesDescargados());
                if (instantanea.bytesTotales() > 0) {
                    tarea.bytesTotalesProperty().set(instantanea.bytesTotales());
                }
                tarea.velocidadBytesPorSegProperty().set(instantanea.velocidadBytesPorSeg());
                tarea.segundosRestantesProperty().set(instantanea.segundosRestantes());
            });
        }

        @Override
        public void archivoFinal(Path ruta) {
            enInterfaz(() -> tarea.archivoFinalProperty().set(ruta));
        }

        @Override
        public void dispositivo(String descripcion) {
            enInterfaz(() -> tarea.dispositivoProperty().set(descripcion));
        }

        @Override
        public void consola(String linea) {
            if (consola != null) {
                consola.registrar(tarea.nombreDelProceso(), linea);
            }
        }

        @Override
        public boolean cancelado() {
            return tarea.estado() == JobStatus.CANCELADA;
        }
    }

    /** Semáforo cuyo número de permisos se puede reducir en caliente. */
    private static final class SemaforoAjustable extends Semaphore {

        SemaforoAjustable(int permisos) {
            super(permisos);
        }

        void reducir(int cantidad) {
            reducePermits(cantidad);
        }
    }

    /** Tareas de un tipo concreto, para los filtros de la vista de la cola. */
    public List<Tarea> deTipo(TipoTarea tipo) {
        return tareas.stream().filter(t -> t.tipo() == tipo).toList();
    }
}
