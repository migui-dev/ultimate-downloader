package com.ultimatedownloader.model;

import java.nio.file.Path;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * Una descarga de la cola. Lo común con conversiones y separaciones está en Tarea; aquí solo
 * la petición de yt-dlp, que puede cambiar al reintentar.
 */
public final class DownloadJob extends Tarea {

    private final ObjectProperty<DownloadRequest> peticion = new SimpleObjectProperty<>();

    public DownloadJob(int id, DownloadRequest peticion) {
        super(id, TipoTarea.DESCARGA,
                peticion.tituloPrevisto() != null && !peticion.tituloPrevisto().isBlank()
                        ? peticion.tituloPrevisto()
                        : peticion.url());
        this.peticion.set(peticion);
        if (peticion.estimacion().conocida()) {
            bytesTotalesProperty().set(peticion.estimacion().bytes());
        }
    }

    public DownloadRequest peticion() {
        return peticion.get();
    }

    public ObjectProperty<DownloadRequest> peticionProperty() {
        return peticion;
    }

    @Override
    public Path carpetaDestino() {
        return peticion.get().carpetaDestino();
    }

    @Override
    public String nombreDelProceso() {
        return "yt-dlp";
    }
}
