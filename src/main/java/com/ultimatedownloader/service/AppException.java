package com.ultimatedownloader.service;

import com.ultimatedownloader.model.ErrorAmistoso;

/**
 * Error ya traducido al español y listo para mostrarse. Los servicios envuelven aquí cualquier
 * fallo técnico, para que la interfaz no tenga que interpretar una IOException.
 */
public class AppException extends Exception {

    private final transient ErrorAmistoso error;

    public AppException(ErrorAmistoso error) {
        super(error.mensaje());
        this.error = error;
    }

    public AppException(ErrorAmistoso error, Throwable causa) {
        super(error.mensaje(), causa);
        this.error = error;
    }

    public AppException(String mensaje, String sugerencia, Throwable causa) {
        this(new ErrorAmistoso(mensaje, sugerencia,
                causa != null ? causa.toString() : null), causa);
    }

    public ErrorAmistoso error() {
        return error;
    }
}
