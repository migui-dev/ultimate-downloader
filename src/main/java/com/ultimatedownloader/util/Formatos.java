package com.ultimatedownloader.util;

import java.util.Locale;

/**
 * Números a texto legible: tamaños, velocidades y tiempos. El separador decimal lo pone el
 * idioma elegido; sería raro leer «1.5 GB» con el resto de la interfaz en español.
 */
public final class Formatos {

    private static final String[] UNIDADES = {"B", "KB", "MB", "GB", "TB"};

    private Formatos() {
    }

    /** Región con la que se formatea; la fija el idioma de la interfaz. */
    private static Locale locale() {
        return Textos.locale();
    }

    /**
     * Tamaño con un decimal siempre: 85,6 MB, 1,3 GB. Las unidades son de 1024, como en el
     * explorador de Windows, pero se salta a la siguiente al pasar de 1000: así no se lee «1024,0 MB».
     */
    public static String tamano(long bytes) {
        if (bytes <= 0) {
            return "—";
        }
        double valor = bytes;
        int unidad = 0;
        while (valor >= 1000 && unidad < UNIDADES.length - 1) {
            valor /= 1024;
            unidad++;
        }
        if (unidad == 0) {
            return String.format(locale(), "%.0f %s", valor, UNIDADES[unidad]);
        }
        return String.format(locale(), "%.1f %s", valor, UNIDADES[unidad]);
    }

    /** Igual que tamano(long) pero admite nulos y marca los valores aproximados. */
    public static String tamano(Long bytes, boolean aproximado) {
        if (bytes == null || bytes <= 0) {
            return "—";
        }
        return (aproximado ? "~" : "") + tamano(bytes);
    }

    public static String velocidad(double bytesPorSegundo) {
        if (bytesPorSegundo <= 0) {
            return "—";
        }
        return tamano(Math.round(bytesPorSegundo)) + "/s";
    }

    /** Duración corta para el tiempo restante: 45 s, 3 min 20 s, 1 h 5 min. */
    public static String duracionCorta(Long segundos) {
        if (segundos == null || segundos < 0) {
            return "—";
        }
        long s = segundos;
        if (s < 60) {
            return s + " s";
        }
        if (s < 3600) {
            return (s / 60) + " min " + (s % 60) + " s";
        }
        return (s / 3600) + " h " + ((s % 3600) / 60) + " min";
    }

    /** Duración de un vídeo: 10:35 o 1:02:17. */
    public static String duracionReloj(long segundos) {
        if (segundos <= 0) {
            return "—";
        }
        long horas = segundos / 3600;
        long minutos = (segundos % 3600) / 60;
        long resto = segundos % 60;
        return horas > 0
                ? String.format(locale(), "%d:%02d:%02d", horas, minutos, resto)
                : String.format(locale(), "%d:%02d", minutos, resto);
    }

    public static String bitrate(double kbps) {
        if (kbps <= 0) {
            return "—";
        }
        // A partir de 1000 kbps se pasa a Mbps: dentro de una misma columna todos los valores
        // de vídeo quedan en la misma unidad y se pueden comparar de un vistazo.
        if (kbps >= 1000) {
            return String.format(locale(), "%.1f Mbps", kbps / 1000);
        }
        return String.format(locale(), "%.0f kbps", kbps);
    }

    public static String fps(double fps) {
        if (fps <= 0) {
            return "—";
        }
        return Math.abs(fps - Math.rint(fps)) < 0.01
                ? String.valueOf(Math.round(fps))
                : String.format(locale(), "%.2f", fps);
    }

    /** Frecuencia de muestreo: 44,1 kHz. */
    public static String frecuencia(int hercios) {
        if (hercios <= 0) {
            return "—";
        }
        return String.format(locale(), "%.1f kHz", hercios / 1000.0);
    }

    /** Número con tantos decimales como se pida, con coma decimal. */
    public static String decimal(double valor, int decimales) {
        return String.format(locale(), "%." + decimales + "f", valor);
    }

    /** Número tal cual, para rellenar un campo: enteros sin decimales y decimales con coma. */
    public static String numeroSuelto(Number valor) {
        if (valor == null) {
            return "";
        }
        double d = valor.doubleValue();
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.format(locale(), "%s", d).replace(".", ",");
    }

    public static String porcentaje(double fraccion) {
        if (fraccion < 0) {
            return "—";
        }
        return String.format(locale(), "%.1f %%", Math.min(1, fraccion) * 100);
    }
}
