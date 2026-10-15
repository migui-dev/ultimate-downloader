package com.ultimatedownloader.service.separacion;

/**
 * Reparte la barra de la instalación entre los pasos que la componen.
 *
 * uv no da un porcentaje global de nada, así que cada paso se queda con un tramo de la barra y
 * dentro de él se avanza con cada noticia que da: un poco de lo que falta cada vez. Así la
 * barra se mueve siempre hacia delante, nunca retrocede y nunca se adelanta al paso siguiente.
 */
final class BarraPorPasos {

    /** Parte de lo que queda del tramo que se recorre con cada noticia. */
    private static final double TIRON = 0.12;

    private double actual;
    private double fin = 1;

    /** Entra en un paso nuevo y devuelve dónde arranca su tramo. */
    double empezar(double desde, double hasta) {
        actual = desde;
        fin = Math.max(desde, hasta);
        return actual;
    }

    /** Avance tras una noticia del paso en curso. */
    double avanzar() {
        actual += (fin - actual) * TIRON;
        return actual;
    }

    double actual() {
        return actual;
    }
}
