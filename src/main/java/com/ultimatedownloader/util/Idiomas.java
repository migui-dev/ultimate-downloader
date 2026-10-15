package com.ultimatedownloader.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Nombres de idioma a partir de los códigos de YouTube (es, en-US, zh-Hans…). El nombre lo da
 * la máquina virtual en el idioma de la interfaz, así que no hay que mantener una tabla de
 * setenta idiomas por cada idioma de la aplicación.
 *
 * El mapa de abajo es la reserva para los códigos que la JVM no reconoce.
 */
public final class Idiomas {

    private static final Map<String, String> NOMBRES = crearMapa();

    private Idiomas() {
    }

    /** Nombre en el idioma de la interfaz, o el propio código si no se conoce. */
    public static String nombre(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        String limpio = codigo.trim();
        String deLaJvm = deLaJvm(limpio);
        if (deLaJvm != null) {
            return deLaJvm;
        }
        String directo = NOMBRES.get(limpio.toLowerCase(Locale.ROOT));
        if (directo != null) {
            return directo;
        }
        int separador = indiceSeparador(limpio);
        if (separador > 0) {
            String base = limpio.substring(0, separador).toLowerCase(Locale.ROOT);
            String region = limpio.substring(separador + 1);
            String nombreBase = NOMBRES.get(base);
            if (nombreBase != null) {
                return nombreBase + " (" + region.toUpperCase(Locale.ROOT) + ")";
            }
        }
        return limpio;
    }

    /**
     * Devuelve null cuando la JVM no conoce el código: en ese caso getDisplayName repite el propio
     * código, y eso no es un nombre.
     */
    private static String deLaJvm(String codigo) {
        Locale idioma = Locale.forLanguageTag(codigo.replace('_', '-'));
        if (idioma.getLanguage().isEmpty()) {
            return null;
        }
        String nombre = idioma.getDisplayName(Textos.locale());
        if (nombre.isBlank() || nombre.equalsIgnoreCase(codigo)
                || nombre.equalsIgnoreCase(idioma.getLanguage())) {
            return null;
        }
        return nombre.substring(0, 1).toUpperCase(Textos.locale()) + nombre.substring(1);
    }

    private static int indiceSeparador(String codigo) {
        int guion = codigo.indexOf('-');
        int bajo = codigo.indexOf('_');
        if (guion < 0) {
            return bajo;
        }
        if (bajo < 0) {
            return guion;
        }
        return Math.min(guion, bajo);
    }

    private static Map<String, String> crearMapa() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("es", "Español");
        m.put("es-es", "Español (España)");
        m.put("es-419", "Español (Latinoamérica)");
        m.put("es-us", "Español (EE. UU.)");
        m.put("en", "Inglés");
        m.put("en-us", "Inglés (EE. UU.)");
        m.put("en-gb", "Inglés (Reino Unido)");
        m.put("ca", "Catalán");
        m.put("gl", "Gallego");
        m.put("eu", "Euskera");
        m.put("pt", "Portugués");
        m.put("pt-br", "Portugués (Brasil)");
        m.put("fr", "Francés");
        m.put("de", "Alemán");
        m.put("it", "Italiano");
        m.put("nl", "Neerlandés");
        m.put("sv", "Sueco");
        m.put("no", "Noruego");
        m.put("nb", "Noruego (bokmal)");
        m.put("da", "Danés");
        m.put("fi", "Finés");
        m.put("is", "Islandés");
        m.put("pl", "Polaco");
        m.put("cs", "Checo");
        m.put("sk", "Eslovaco");
        m.put("hu", "Húngaro");
        m.put("ro", "Rumano");
        m.put("bg", "Búlgaro");
        m.put("el", "Griego");
        m.put("ru", "Ruso");
        m.put("uk", "Ucraniano");
        m.put("be", "Bielorruso");
        m.put("sr", "Serbio");
        m.put("hr", "Croata");
        m.put("sl", "Esloveno");
        m.put("lt", "Lituano");
        m.put("lv", "Letón");
        m.put("et", "Estonio");
        m.put("tr", "Turco");
        m.put("ar", "Árabe");
        m.put("he", "Hebreo");
        m.put("iw", "Hebreo");
        m.put("fa", "Persa");
        m.put("ur", "Urdu");
        m.put("hi", "Hindi");
        m.put("bn", "Bengalí");
        m.put("pa", "Panyabí");
        m.put("gu", "Guyaratí");
        m.put("mr", "Maratí");
        m.put("ta", "Tamil");
        m.put("te", "Telugu");
        m.put("kn", "Canarés");
        m.put("ml", "Malayalam");
        m.put("si", "Cingalés");
        m.put("ne", "Nepalí");
        m.put("th", "Tailandés");
        m.put("vi", "Vietnamita");
        m.put("id", "Indonesio");
        m.put("ms", "Malayo");
        m.put("fil", "Filipino");
        m.put("tl", "Tagalo");
        m.put("ja", "Japonés");
        m.put("ko", "Coreano");
        m.put("zh", "Chino");
        m.put("zh-hans", "Chino (simplificado)");
        m.put("zh-hant", "Chino (tradicional)");
        m.put("zh-cn", "Chino (China)");
        m.put("zh-tw", "Chino (Taiwán)");
        m.put("sw", "Suajili");
        m.put("af", "Afrikáans");
        m.put("am", "Amárico");
        m.put("ha", "Hausa");
        m.put("ig", "Igbo");
        m.put("yo", "Yoruba");
        m.put("zu", "Zulú");
        m.put("az", "Azerbaiyano");
        m.put("kk", "Kazajo");
        m.put("uz", "Uzbeko");
        m.put("hy", "Armenio");
        m.put("ka", "Georgiano");
        m.put("sq", "Albanés");
        m.put("mk", "Macedonio");
        m.put("mn", "Mongol");
        m.put("km", "Jemer");
        m.put("lo", "Lao");
        m.put("my", "Birmano");
        m.put("la", "Latín");
        return m;
    }
}
