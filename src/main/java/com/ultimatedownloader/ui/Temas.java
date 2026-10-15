package com.ultimatedownloader.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;

/**
 * Tema oscuro (el de por defecto) y claro. De AtlantaFX se aprovecha solo la estructura de los
 * controles: el color lo ponen tokens.css y app.css, que se añaden después y lo pisan.
 */
public final class Temas {

    public static final String CLASE_OSCURO = "tema-oscuro";
    public static final String CLASE_CLARO = "tema-claro";

    private static final String TOKENS = "/com/ultimatedownloader/css/tokens.css";
    private static final String COMPONENTES = "/com/ultimatedownloader/css/app.css";

    /**
     * Último tema aplicado. La aplicación tiene uno solo a la vez, así que recordarlo evita ir
     * pasándolo a mano a cada aviso que se abre desde cualquier rincón.
     */
    private static volatile boolean oscuro = true;

    private Temas() {
    }

    /** El tema que está puesto ahora mismo. */
    public static boolean esOscuro() {
        return oscuro;
    }

    /** Aplica el tema a una escena; si es null, solo cambia la hoja global. */
    public static void aplicar(Scene escena, boolean oscuro) {
        Temas.oscuro = oscuro;
        Application.setUserAgentStylesheet(oscuro
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());

        marcar(escena, oscuro);
    }

    /** Pone en la escena la clase del tema, sin tocar la hoja global. */
    static void marcar(Scene escena, boolean oscuro) {
        if (escena == null || escena.getRoot() == null) {
            return;
        }
        var clases = escena.getRoot().getStyleClass();
        clases.removeAll(CLASE_OSCURO, CLASE_CLARO);
        clases.add(oscuro ? CLASE_OSCURO : CLASE_CLARO);
    }

    /** Prepara una escena: tipografías y hojas propias, en el orden correcto. */
    public static void preparar(Scene escena, boolean oscuro) {
        Fuentes.cargar();
        for (String hoja : new String[]{TOKENS, COMPONENTES}) {
            String url = Temas.class.getResource(hoja).toExternalForm();
            if (!escena.getStylesheets().contains(url)) {
                escena.getStylesheets().add(url);
            }
        }
        if (!escena.getRoot().getStyleClass().contains("raiz")) {
            escena.getRoot().getStyleClass().add("raiz");
        }
        aplicar(escena, oscuro);
    }
}
