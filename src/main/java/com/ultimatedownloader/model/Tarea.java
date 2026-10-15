package com.ultimatedownloader.model;

import java.nio.file.Path;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Un trabajo de la cola: una descarga, una conversión o una separación. Cada módulo añade sus
 * propios datos heredando de aquí.
 *
 * Las propiedades solo se tocan desde el hilo de JavaFX; quien las cambia es ColaService.
 */
public abstract class Tarea {

    private final int id;
    private final TipoTarea tipo;

    private final StringProperty titulo = new SimpleStringProperty("");
    private final ObjectProperty<JobStatus> estado = new SimpleObjectProperty<>(JobStatus.EN_COLA);
    private final ObjectProperty<DownloadPhase> fase = new SimpleObjectProperty<>(DownloadPhase.EN_COLA);
    private final DoubleProperty progreso = new SimpleDoubleProperty(-1);
    private final DoubleProperty velocidadBytesPorSeg = new SimpleDoubleProperty(0);
    private final ObjectProperty<Long> segundosRestantes = new SimpleObjectProperty<>();
    private final LongProperty bytesDescargados = new SimpleLongProperty(0);
    private final LongProperty bytesTotales = new SimpleLongProperty(0);
    private final StringProperty mensaje = new SimpleStringProperty("");
    private final StringProperty sugerencia = new SimpleStringProperty();
    private final StringProperty dispositivo = new SimpleStringProperty();
    private final ObjectProperty<Path> archivoFinal = new SimpleObjectProperty<>();

    protected Tarea(int id, TipoTarea tipo, String titulo) {
        this.id = id;
        this.tipo = tipo;
        this.titulo.set(titulo != null ? titulo : "");
    }

    public int id() {
        return id;
    }

    public TipoTarea tipo() {
        return tipo;
    }

    /** Carpeta donde va a dejar su resultado. */
    public abstract Path carpetaDestino();

    /** Nombre del proceso externo que ejecuta, para etiquetar sus líneas en la consola. */
    public abstract String nombreDelProceso();

    // ------------------------------------------------------------------ propiedades

    public StringProperty tituloProperty() {
        return titulo;
    }

    public ObjectProperty<JobStatus> estadoProperty() {
        return estado;
    }

    public JobStatus estado() {
        return estado.get();
    }

    public ObjectProperty<DownloadPhase> faseProperty() {
        return fase;
    }

    public DownloadPhase fase() {
        return fase.get();
    }

    public DoubleProperty progresoProperty() {
        return progreso;
    }

    public DoubleProperty velocidadBytesPorSegProperty() {
        return velocidadBytesPorSeg;
    }

    public ObjectProperty<Long> segundosRestantesProperty() {
        return segundosRestantes;
    }

    public LongProperty bytesDescargadosProperty() {
        return bytesDescargados;
    }

    public LongProperty bytesTotalesProperty() {
        return bytesTotales;
    }

    public StringProperty mensajeProperty() {
        return mensaje;
    }

    public StringProperty sugerenciaProperty() {
        return sugerencia;
    }

    /** Dónde se está ejecutando: «CUDA · RTX 5070», «CPU»… Vacío si no aplica. */
    public StringProperty dispositivoProperty() {
        return dispositivo;
    }

    public ObjectProperty<Path> archivoFinalProperty() {
        return archivoFinal;
    }

    public Path archivoFinal() {
        return archivoFinal.get();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + ", " + estado.get() + ", " + titulo.get() + "]";
    }
}
