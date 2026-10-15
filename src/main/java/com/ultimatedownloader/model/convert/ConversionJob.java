package com.ultimatedownloader.model.convert;

import com.ultimatedownloader.model.Tarea;
import com.ultimatedownloader.model.TipoTarea;
import java.nio.file.Path;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/** Una conversión dentro de la cola. */
public final class ConversionJob extends Tarea {

    private final ObjectProperty<ConversionRequest> peticion = new SimpleObjectProperty<>();

    public ConversionJob(int id, ConversionRequest peticion) {
        super(id, TipoTarea.CONVERSION, peticion.origen().getFileName().toString());
        this.peticion.set(peticion);
    }

    public ConversionRequest peticion() {
        return peticion.get();
    }

    public ObjectProperty<ConversionRequest> peticionProperty() {
        return peticion;
    }

    @Override
    public Path carpetaDestino() {
        return peticion.get().carpetaDestino();
    }

    @Override
    public String nombreDelProceso() {
        return "ffmpeg";
    }
}
