package com.ultimatedownloader.model.separacion;

import com.ultimatedownloader.model.Tarea;
import com.ultimatedownloader.model.TipoTarea;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/** Una separación dentro de la cola. */
public final class SeparacionJob extends Tarea {

    private final ObjectProperty<SeparacionRequest> peticion = new SimpleObjectProperty<>();

    /** Temporales creados para esta tarea (audio extraído, recorte de la muestra). */
    private final List<Path> temporales = new ArrayList<>();

    /** Pistas que ha escrito el motor, para poder abrirlas al terminar. */
    private final List<Path> resultados = new ArrayList<>();

    public SeparacionJob(int id, SeparacionRequest peticion) {
        super(id, TipoTarea.SEPARACION, peticion.entrada().getFileName().toString());
        this.peticion.set(peticion);
    }

    public SeparacionRequest peticion() {
        return peticion.get();
    }

    public ObjectProperty<SeparacionRequest> peticionProperty() {
        return peticion;
    }

    public synchronized void anadirTemporal(Path ruta) {
        temporales.add(ruta);
    }

    public synchronized List<Path> temporales() {
        return List.copyOf(temporales);
    }

    public synchronized void anadirResultado(Path ruta) {
        resultados.add(ruta);
    }

    public synchronized List<Path> resultados() {
        return List.copyOf(resultados);
    }

    @Override
    public Path carpetaDestino() {
        return peticion.get().carpetaDestino();
    }

    @Override
    public String nombreDelProceso() {
        return "audio-separator";
    }
}
