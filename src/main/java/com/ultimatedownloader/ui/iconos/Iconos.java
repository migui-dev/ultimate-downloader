package com.ultimatedownloader.ui.iconos;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

/**
 * Crea los iconos a partir de los trazos de Lucide. Cada trazo va en su propio SVGPath dentro
 * de un grupo: unirlos rompía los comandos relativos y deformaba los dibujos.
 *
 * El color no se fija aquí, lo pone el CSS, así que un icono dentro de un botón primario se
 * tiñe solo igual que el texto que lo acompaña.
 */
public final class Iconos {

    /** Tamaño habitual en botones y en la barra lateral. */
    public static final double NORMAL = 16;

    private Iconos() {
    }

    public static Node de(Lucide icono) {
        return de(icono, NORMAL);
    }

    /** El tamaño es el lado en píxeles; los trazos están dibujados en una rejilla de 24. */
    public static Node de(Lucide icono, double tamano) {
        Group trazos = new Group();

        // Marco invisible de 24×24: fija los límites al lienzo original para que todos los
        // iconos queden centrados igual, sin importar cuánto ocupe el dibujo de cada uno.
        Rectangle marco =
                new Rectangle(0, 0, Lucide.REJILLA, Lucide.REJILLA);
        marco.setFill(Color.TRANSPARENT);
        marco.setStroke(null);
        trazos.getChildren().add(marco);

        for (String ruta : icono.trazos()) {
            SVGPath trazo = new SVGPath();
            trazo.setContent(ruta);
            trazo.getStyleClass().add("icono");
            trazos.getChildren().add(trazo);
        }

        // Los trazos vienen en una rejilla de 24: se escalan al tamaño pedido, con lo que el
        // grosor de 2 queda proporcional (1,33 px a 16 px), que es como los dibuja Lucide.
        double escala = tamano / Lucide.REJILLA;
        trazos.setScaleX(escala);
        trazos.setScaleY(escala);

        StackPane caja = new StackPane(trazos);
        caja.getStyleClass().add("caja-icono");
        caja.setMinSize(tamano, tamano);
        caja.setPrefSize(tamano, tamano);
        caja.setMaxSize(tamano, tamano);
        caja.setMouseTransparent(true);
        return caja;
    }

    /** Icono con una clase de estilo extra, para teñirlo de forma distinta. */
    public static Node de(Lucide icono, double tamano, String claseExtra) {
        Node nodo = de(icono, tamano);
        nodo.getStyleClass().add(claseExtra);
        return nodo;
    }
}
