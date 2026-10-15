package com.ultimatedownloader.model;

import java.util.Comparator;

/** Una pista de vídeo de las que ofrece YouTube. Lo que no venga en el JSON queda a 0 o null. */
public record VideoFormat(
        String formatId,
        int ancho,
        int alto,
        double fps,
        VideoCodec codec,
        String codecCrudo,
        String rangoDinamico,
        double bitrateKbps,
        String contenedor,
        String protocolo,
        Long tamanoBytes,
        boolean tamanoExacto,
        String nota,
        AudioCodec audioIncluido) implements PistaDescargable {

    /**
     * De mejor a peor: resolución, fps, HDR, descarga directa, códec y bitrate.
     *
     * El códec pesa más que el bitrate a propósito, igual que en yt-dlp: AV1 a 9 Mbps se ve como
     * VP9 a 17 y ocupa la mitad, así que la primera fila coincide con «Mejor disponible».
     */
    public static final Comparator<VideoFormat> MEJOR_PRIMERO = Comparator
            .comparingInt(VideoFormat::alto).reversed()
            .thenComparing(Comparator.comparingInt(VideoFormat::ancho).reversed())
            .thenComparing(Comparator.comparingDouble(VideoFormat::fps).reversed())
            .thenComparing(Comparator.comparing(VideoFormat::hdr).reversed())
            .thenComparing(Comparator.comparingInt((VideoFormat f) -> f.esProgresivo() ? 1 : 0).reversed())
            .thenComparing(Comparator.comparingInt((VideoFormat f) -> f.codec().preferencia()).reversed())
            .thenComparing(Comparator.comparingDouble(VideoFormat::bitrateKbps).reversed())
            .thenComparing(VideoFormat::formatId);

    /**
     * Fila «Mejor disponible», la primera de la tabla y la que viene marcada. Se reconoce porque
     * no tiene identificador de formato.
     */
    public static VideoFormat mejorDisponible() {
        return new VideoFormat(null, 0, 0, 0, VideoCodec.DESCONOCIDO, null, null, 0,
                null, null, null, false, null, AudioCodec.NINGUNO);
    }

    @Override
    public boolean esMejorDisponible() {
        return formatId == null;
    }

    public boolean hdr() {
        return rangoDinamico != null
                && !rangoDinamico.isBlank()
                && !"SDR".equalsIgnoreCase(rangoDinamico);
    }

    /** Los formatos HLS de YouTube son redundantes y no traen tamaño: se prefieren los HTTPS. */
    public boolean esHls() {
        return protocolo != null && protocolo.toLowerCase(java.util.Locale.ROOT).contains("m3u8");
    }

    /** Descarga directa por HTTPS, que es la vía preferida. */
    public boolean esProgresivo() {
        return !esHls();
    }

    /** true si el formato ya trae audio y no hace falta unir una segunda pista. */
    public boolean incluyeAudio() {
        return audioIncluido != null
                && audioIncluido != AudioCodec.NINGUNO
                && audioIncluido != AudioCodec.DESCONOCIDO;
    }

    /** 2160p60, 1080p… calculado a partir de la altura y los fps. */
    public String etiquetaCalidad() {
        if (alto <= 0) {
            return nota != null && !nota.isBlank() ? nota : formatId;
        }
        String base = alto + "p";
        if (fps >= 50) {
            base += String.valueOf(Math.round(fps));
        }
        return base;
    }

    public String resolucionTexto() {
        return ancho > 0 && alto > 0 ? ancho + "×" + alto : "—";
    }

    public String rangoDinamicoTexto() {
        if (rangoDinamico == null || rangoDinamico.isBlank()) {
            return "—";
        }
        return "SDR".equalsIgnoreCase(rangoDinamico) ? "SDR" : rangoDinamico;
    }

    /** Texto del contenedor con el protocolo entre paréntesis cuando no es descarga directa. */
    public String contenedorTexto() {
        String base = contenedor != null ? contenedor : "—";
        return esHls() ? base + " (HLS)" : base;
    }
}
