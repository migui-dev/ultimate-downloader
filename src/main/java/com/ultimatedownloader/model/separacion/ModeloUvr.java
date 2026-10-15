package com.ultimatedownloader.model.separacion;

import com.ultimatedownloader.util.Textos;
import java.util.List;
import java.util.Locale;

/** Un modelo de los que lista «audio-separator --list_models». */
public record ModeloUvr(String nombre, String fichero, Arquitectura arquitectura,
                        List<String> pistas, String pistaPrincipal,
                        double sdrVoces, double sdrInstrumental, List<String> archivos) {

    public ModeloUvr {
        pistas = pistas == null ? List.of() : List.copyOf(pistas);
        archivos = archivos == null ? List.of() : List.copyOf(archivos);
    }

    /**
     * SDR que se enseña en la tabla: el de la voz, que es el que publica todo el mundo. Si solo
     * está medido el instrumental, se usa ese.
     */
    public double sdr() {
        return sdrVoces > 0 ? sdrVoces : sdrInstrumental;
    }

    /** Si sirve para quedarse con la voz y el instrumental por separado. */
    public boolean separaVozEInstrumental() {
        return pistas.contains("vocals") || pistas.contains("instrumental");
    }

    /** Nombre corto para las listas: el nombre largo sin el prefijo de la arquitectura. */
    public String nombreCorto() {
        int dosPuntos = nombre.indexOf(": ");
        return dosPuntos > 0 ? nombre.substring(dosPuntos + 2) : nombre;
    }

    /** Pistas en español y con mayúscula, para enseñarlas: «Voz, Instrumental». */
    public String pistasLegibles() {
        if (pistas.isEmpty()) {
            return "—";
        }
        return String.join(", ", pistas.stream().map(ModeloUvr::nombrePista).toList());
    }

    /** Nombre de una pista en el idioma elegido. */
    public static String nombrePista(String pista) {
        String clave = pista.toLowerCase(Locale.ROOT).replace(" ", "_");
        // Lo que no esté traducido se enseña tal cual: el catálogo trae pistas raras de vez en
        // cuando y es mejor leer «guitar» que una clave o un hueco.
        return Textos.deEnum("pista", clave,
                pista.substring(0, 1).toUpperCase(Locale.ROOT) + pista.substring(1));
    }

    /** --single_stem espera el nombre en inglés y con mayúscula inicial. */
    public static String claveParaSingleStem(String pista) {
        if (pista == null || pista.isBlank()) {
            return null;
        }
        String[] palabras = pista.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String palabra : palabras) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(palabra.substring(0, 1).toUpperCase(Locale.ROOT)).append(palabra.substring(1));
        }
        return sb.toString();
    }
}
