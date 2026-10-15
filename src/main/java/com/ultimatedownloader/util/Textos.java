package com.ultimatedownloader.util;

import com.ultimatedownloader.model.Idioma;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Textos de la interfaz en el idioma elegido. Todo lo que lee una persona sale de aquí: los
 * FXML con la sintaxis %clave y el código con t().
 *
 * El fichero base está en español, así que una clave que falte en otro idioma sale en español
 * en vez de romper la pantalla. Cambiar de idioma cambia también el formato de los números.
 */
public final class Textos {

    private static final Logger log = LoggerFactory.getLogger(Textos.class);

    private static final String BASE = "com.ultimatedownloader.i18n.textos";

    private static volatile Idioma idioma = Idioma.PREDETERMINADO;
    private static volatile Locale locale = Idioma.PREDETERMINADO.locale();
    private static volatile ResourceBundle paquete = cargar(locale);

    private Textos() {
    }

    /**
     * Cambia el idioma de toda la aplicación. También mueve el Locale de la máquina virtual, del
     * que dependen los selectores de archivos y el orden alfabético.
     */
    public static void usar(Idioma elegido) {
        Idioma efectivo = (elegido != null ? elegido : Idioma.PREDETERMINADO).efectivo();
        idioma = efectivo;
        locale = efectivo.locale();
        Locale.setDefault(Locale.Category.DISPLAY, locale);
        Locale.setDefault(Locale.Category.FORMAT, locale);
        paquete = cargar(locale);
        log.info("Idioma de la interfaz: {}", efectivo.nombrePropio());
    }

    /** Idioma que se está usando de verdad, ya resuelto si era «como el sistema». */
    public static Idioma idioma() {
        return idioma;
    }

    /** Región con la que se formatean números, tamaños y fechas. */
    public static Locale locale() {
        return locale;
    }

    /** El paquete actual; es lo que hay que pasarle al FXMLLoader. */
    public static ResourceBundle paquete() {
        return paquete;
    }

    /** Texto de una clave. Si falta en todas partes se devuelve la clave, que se ve y se arregla. */
    public static String t(String clave) {
        try {
            return paquete.getString(clave);
        } catch (MissingResourceException e) {
            log.warn("Falta el texto «{}»", clave);
            return clave;
        }
    }

    /**
     * Texto con huecos, al estilo de MessageFormat. Van numerados a propósito: cada idioma coloca
     * las palabras donde le conviene, y así una traducción puede darles la vuelta.
     */
    public static String t(String clave, Object... valores) {
        if (valores == null || valores.length == 0) {
            return t(clave);
        }
        return new MessageFormat(t(clave), locale).format(valores);
    }

    /**
     * Nombre legible de una constante de un enumerado. Muchas son nombres propios que no se
     * traducen («Opus», «MP4»), así que al fichero de idioma solo van las que haga falta: lo que
     * no esté se queda con el nombre técnico.
     */
    public static String deEnum(String tipo, String constante, String porDefecto) {
        String clave = "enum." + tipo + "." + constante.toLowerCase(Locale.ROOT);
        return paquete.containsKey(clave) ? paquete.getString(clave) : porDefecto;
    }

    /**
     * Carga los textos del idioma pedido, sin dejar que ResourceBundle recurra al del sistema.
     *
     * Por defecto, cuando no existe el fichero exacto —y no existe textos_es, porque el español
     * es el fichero base— ResourceBundle prueba con el idioma del equipo antes que con la base.
     * En un Windows en español daba igual, pero en un equipo en inglés la aplicación salía
     * entera en inglés aunque se hubiera elegido español.
     */
    private static ResourceBundle cargar(Locale destino) {
        return ResourceBundle.getBundle(BASE, destino,
                ResourceBundle.Control.getNoFallbackControl(
                        ResourceBundle.Control.FORMAT_PROPERTIES));
    }
}
