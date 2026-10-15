package com.ultimatedownloader.model;

import java.util.Comparator;

/** Una pista de audio de las que ofrece YouTube. Lo que no venga en el JSON queda a null. */
public record AudioFormat(
        String formatId,
        AudioCodec codec,
        String codecCrudo,
        double bitrateKbps,
        Integer frecuenciaHz,
        Integer canales,
        String idioma,
        String nombreIdioma,
        boolean pistaOriginal,
        String contenedor,
        String protocolo,
        Long tamanoBytes,
        boolean tamanoExacto,
        String nota) implements PistaDescargable {

    /** Anchura del escalón de bitrate, en kbps, para considerar dos pistas equivalentes. */
    private static final int ESCALON_KBPS = 16;

    /**
     * De mejor a peor: pista original, escalón de bitrate, códec, sin DRC y descarga directa.
     *
     * El bitrate se agrupa en escalones de 16 kbps antes de mirar el códec, porque Opus a 128
     * suena mejor que AAC a 129 y no tiene sentido que un kilobit decida el orden.
     */
    public static final Comparator<AudioFormat> MEJOR_PRIMERO = Comparator
            .comparing(AudioFormat::pistaOriginal).reversed()
            .thenComparing(Comparator.comparingLong(AudioFormat::escalonBitrate).reversed())
            .thenComparing(Comparator.comparingInt((AudioFormat f) -> f.codec().preferencia()).reversed())
            .thenComparing(Comparator.comparingInt((AudioFormat f) -> f.esDrc() ? 1 : 0))
            .thenComparing(Comparator.comparingInt((AudioFormat f) -> f.esProgresivo() ? 1 : 0).reversed())
            .thenComparing(Comparator.comparingDouble(AudioFormat::bitrateKbps).reversed())
            .thenComparing(AudioFormat::formatId);

    /** Fila «Mejor disponible» de la tabla de audio. */
    public static AudioFormat mejorDisponible() {
        return new AudioFormat(null, AudioCodec.DESCONOCIDO, null, 0, null, null, null, null,
                false, null, null, null, false, null);
    }

    @Override
    public boolean esMejorDisponible() {
        return formatId == null;
    }

    /** Bitrate redondeado a escalones, para que diferencias mínimas no cambien el orden. */
    public long escalonBitrate() {
        return Math.round(bitrateKbps / ESCALON_KBPS);
    }

    public boolean esHls() {
        return protocolo != null && protocolo.toLowerCase(java.util.Locale.ROOT).contains("m3u8");
    }

    /** Descarga directa por HTTPS, que es la vía preferida. */
    public boolean esProgresivo() {
        return !esHls();
    }

    /**
     * Las variantes «DRC» traen el volumen igualado; para archivar interesa más la original,
     * así que se quedan por debajo en el orden.
     */
    public boolean esDrc() {
        return formatId != null && formatId.contains("-drc");
    }

    /** Texto para la columna de idioma: nombre, código o un guion si el vídeo tiene una sola pista. */
    public String idiomaTexto() {
        if (nombreIdioma != null && !nombreIdioma.isBlank()) {
            return pistaOriginal ? nombreIdioma + " (original)" : nombreIdioma;
        }
        if (idioma != null && !idioma.isBlank()) {
            return idioma;
        }
        return "—";
    }

    public String canalesTexto() {
        if (canales == null || canales <= 0) {
            return "—";
        }
        return switch (canales) {
            case 1 -> "Mono";
            case 2 -> "Estéreo";
            default -> canales + " canales";
        };
    }

    /** Texto del contenedor con el protocolo entre paréntesis cuando no es descarga directa. */
    public String contenedorTexto() {
        String base = contenedor != null ? contenedor : "—";
        return esHls() ? base + " (HLS)" : base;
    }
}
