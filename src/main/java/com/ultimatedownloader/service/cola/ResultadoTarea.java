package com.ultimatedownloader.service.cola;

import com.ultimatedownloader.model.ErrorAmistoso;

/** Cómo ha acabado un intento: bien, cancelado o con un error, y si conviene repetirlo. */
public record ResultadoTarea(boolean exito, boolean cancelada, ErrorAmistoso error,
                             boolean pasajero) {

    public static final ResultadoTarea CORRECTO = new ResultadoTarea(true, false, null, false);
    public static final ResultadoTarea CANCELADA = new ResultadoTarea(false, true, null, false);

    public static ResultadoTarea fallo(ErrorAmistoso error) {
        return new ResultadoTarea(false, false, error, false);
    }

    /** Fallo que merece la pena repetir tal cual, como un 403 momentáneo de YouTube. */
    public static ResultadoTarea falloPasajero(ErrorAmistoso error) {
        return new ResultadoTarea(false, false, error, true);
    }
}
