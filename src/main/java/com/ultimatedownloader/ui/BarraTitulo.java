package com.ultimatedownloader.ui;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.ultimatedownloader.service.Plataforma;
import java.util.UUID;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pone la barra de título de Windows en oscuro. JavaFX no sabe nada de ella, la dibuja el
 * gestor de ventanas, así que se le pide al DWM con DwmSetWindowAttribute.
 *
 * En Linux y macOS no hace nada: ahí el marco de la ventana ya sigue al tema del sistema. Y si
 * falla en Windows se registra y se sigue con la barra clara, que es un detalle estético.
 */
public final class BarraTitulo {

    private static final Logger log = LoggerFactory.getLogger(BarraTitulo.class);

    /** Atributo del DWM para el modo oscuro (20 desde Windows 10 2004; antes era 19). */
    private static final int MODO_OSCURO = 20;
    private static final int MODO_OSCURO_ANTIGUO = 19;

    private BarraTitulo() {
    }

    /** Interfaz mínima de dwmapi.dll. */
    private interface Dwmapi extends Library {
        Dwmapi INSTANCIA = Native.load("dwmapi", Dwmapi.class);

        int DwmSetWindowAttribute(WinDef.HWND ventana, int atributo, IntByReference valor, int tamano);
    }

    /** La ventana tiene que estar ya visible: antes no existe el identificador nativo. */
    public static void aplicar(Stage ventana, boolean oscuro) {
        if (!Plataforma.esWindows() || ventana == null || !ventana.isShowing()) {
            return;
        }
        try {
            WinDef.HWND identificador = identificadorDe(ventana);
            if (identificador == null) {
                log.debug("No se ha encontrado la ventana nativa de «{}»", ventana.getTitle());
                return;
            }
            IntByReference valor = new IntByReference(oscuro ? 1 : 0);
            int resultado = Dwmapi.INSTANCIA.DwmSetWindowAttribute(
                    identificador, MODO_OSCURO, valor, 4);
            if (resultado != 0) {
                // Windows 10 anteriores a la 2004 usaban otro número de atributo.
                resultado = Dwmapi.INSTANCIA.DwmSetWindowAttribute(
                        identificador, MODO_OSCURO_ANTIGUO, valor, 4);
            }
            if (resultado != 0) {
                log.debug("El DWM ha rechazado el cambio de tema (código {})", resultado);
                return;
            }
            // El marco no se repinta solo: un cambio mínimo de tamaño lo fuerza.
            forzarRepintado(ventana);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError | RuntimeException e) {
            log.debug("No se ha podido cambiar el tema de la barra de título: {}", e.getMessage());
        }
    }

    /**
     * Localiza la ventana nativa por su título. Para que no se confunda con otra que se llame
     * igual, se le pone un título único un instante y se restaura enseguida.
     */
    private static WinDef.HWND identificadorDe(Stage ventana) {
        String titulo = ventana.getTitle();
        String temporal = "ud-" + UUID.randomUUID();
        ventana.setTitle(temporal);
        try {
            return User32.INSTANCE.FindWindow(null, temporal);
        } finally {
            ventana.setTitle(titulo);
        }
    }

    private static void forzarRepintado(Stage ventana) {
        double ancho = ventana.getWidth();
        if (ancho <= 0) {
            return;
        }
        ventana.setWidth(ancho + 1);
        ventana.setWidth(ancho);
    }
}
