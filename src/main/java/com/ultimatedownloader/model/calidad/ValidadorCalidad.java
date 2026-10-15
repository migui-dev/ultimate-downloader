package com.ultimatedownloader.model.calidad;

import com.ultimatedownloader.util.Textos;
import java.util.ArrayList;
import java.util.List;

/**
 * Avisa cuando lo que se pide es más de lo que tiene el original.
 *
 * Subir la resolución, la frecuencia o los bits por encima del archivo de partida no añade
 * información, solo tamaño. La aplicación no lo impide, pero lo dice con todas las letras.
 */
public final class ValidadorCalidad {

    private ValidadorCalidad() {
    }

    /** Datos del archivo de partida que importan aquí. Lo que esté a 0 es que no aplica. */
    public record Origen(int ancho, int alto, double fps, int frecuenciaHz, int bits,
                         int canales, boolean conPerdida) {
    }

    /** Lo que se ha pedido como salida. Los valores a 0 significan «dejar como está». */
    public record Destino(int ancho, int alto, double fps, int frecuenciaHz, int bits,
                          int canales) {

        /** Salida que conserva todo lo del original. */
        public static Destino comoElOriginal() {
            return new Destino(0, 0, 0, 0, 0, 0);
        }
    }

    /** Los avisos, en español y listos para mostrar; lista vacía si no hay nada que decir. */
    public static List<String> comprobar(Origen origen, Destino destino, PerfilCalidad perfil) {
        List<String> avisos = new ArrayList<>();
        if (origen == null) {
            return avisos;
        }

        if (destino != null) {
            comprobarResolucion(origen, destino, avisos);
            comprobarFps(origen, destino, avisos);
            comprobarFrecuencia(origen, destino, avisos);
            comprobarBits(origen, destino, avisos);
            comprobarCanales(origen, destino, avisos);
        }

        if (perfil != null && perfil.sinPerdida() && origen.conPerdida()) {
            avisos.add(Textos.t("aviso.origen_con_perdida", perfil.formato().nombreLegible()));
        }

        if (perfil != null && perfil.nivel() == NivelCalidad.MAXIMA
                && !perfil.sinPerdida() && perfil.formato().esAudio()) {
            avisos.add(Textos.t("aviso.maxima_con_perdida", perfil.formato().nombreLegible()));
        }

        return avisos;
    }

    private static void comprobarResolucion(Origen origen, Destino destino, List<String> avisos) {
        if (origen.alto() <= 0 || destino.alto() <= 0) {
            return;
        }
        if (destino.alto() > origen.alto() || destino.ancho() > origen.ancho()) {
            avisos.add(Textos.t("aviso.escalar",
                    origen.ancho() + "×" + origen.alto(),
                    destino.ancho() + "×" + destino.alto()));
        }
    }

    private static void comprobarFps(Origen origen, Destino destino, List<String> avisos) {
        if (origen.fps() <= 0 || destino.fps() <= 0) {
            return;
        }
        if (destino.fps() > origen.fps() + 0.01) {
            avisos.add(Textos.t("aviso.fps", redondear(origen.fps()), redondear(destino.fps())));
        }
    }

    private static void comprobarFrecuencia(Origen origen, Destino destino, List<String> avisos) {
        if (origen.frecuenciaHz() <= 0 || destino.frecuenciaHz() <= 0) {
            return;
        }
        if (destino.frecuenciaHz() > origen.frecuenciaHz()) {
            avisos.add(Textos.t("aviso.frecuencia", kHz(destino.frecuenciaHz()),
                    kHz(origen.frecuenciaHz())));
        }
    }

    private static void comprobarBits(Origen origen, Destino destino, List<String> avisos) {
        if (origen.bits() <= 0 || destino.bits() <= 0) {
            return;
        }
        if (destino.bits() > origen.bits()) {
            avisos.add(Textos.t("aviso.bits", destino.bits(), origen.bits()));
        }
    }

    private static void comprobarCanales(Origen origen, Destino destino, List<String> avisos) {
        if (origen.canales() <= 0 || destino.canales() <= 0) {
            return;
        }
        if (destino.canales() > origen.canales()) {
            avisos.add(Textos.t("aviso.canales", origen.canales(), destino.canales()));
        }
    }

    private static String redondear(double valor) {
        return Math.abs(valor - Math.rint(valor)) < 0.01
                ? String.valueOf(Math.round(valor))
                : String.format(java.util.Locale.forLanguageTag("es-ES"), "%.2f", valor);
    }

    private static String kHz(int hz) {
        return String.format(java.util.Locale.forLanguageTag("es-ES"), "%.1f kHz", hz / 1000.0);
    }
}
