package com.ultimatedownloader.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Animaciones breves para que los paneles no aparezcan de golpe. JavaFX 21 no admite
 * transiciones en CSS, así que se hacen aquí; duran menos de dos décimas.
 */
public final class Animaciones {

    private static final Duration DURACION = Duration.millis(180);

    private Animaciones() {
    }

    /** Hace aparecer un nodo con un fundido y un desplazamiento mínimo hacia arriba. */
    public static void aparecer(Node nodo) {
        FadeTransition fundido = new FadeTransition(DURACION, nodo);
        fundido.setFromValue(0);
        fundido.setToValue(1);

        TranslateTransition desplazamiento = new TranslateTransition(DURACION, nodo);
        desplazamiento.setFromY(8);
        desplazamiento.setToY(0);

        ParallelTransition conjunto = new ParallelTransition(fundido, desplazamiento);
        conjunto.setInterpolator(Interpolator.EASE_OUT);
        conjunto.play();
    }
}
