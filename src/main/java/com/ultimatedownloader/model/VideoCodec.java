package com.ultimatedownloader.model;

import com.ultimatedownloader.util.Textos;

/** Códecs de vídeo que entrega YouTube, con un nombre legible para la interfaz. */
public enum VideoCodec {

    AV1("AV1", "AV1", 4),
    VP9("VP9", "VP9", 3),
    H265("H.265 / HEVC", "H265", 2),
    H264("H.264 / AVC", "H264", 1),
    VP8("VP8", "VP8", 0),
    DESCONOCIDO("Desconocido", "codec", -1),
    NINGUNO("Sin vídeo", "sin-video", -2);

    private final String nombreLegible;
    private final String nombreCorto;
    private final int preferencia;

    VideoCodec(String nombreLegible, String nombreCorto, int preferencia) {
        this.nombreLegible = nombreLegible;
        this.nombreCorto = nombreCorto;
        this.preferencia = preferencia;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("video_codec", name(), nombreLegible);
    }

    /** Nombre sin espacios ni barras, apto para usarlo en un nombre de archivo. */
    public String nombreCorto() {
        return nombreCorto;
    }

    /** Mayor es mejor; solo se usa para desempatar formatos de la misma calidad. */
    public int preferencia() {
        return preferencia;
    }

    /**
     * Traduce el valor vcodec del JSON de yt-dlp, que llega como cadena técnica
     * (av01.0.13M.08, vp09.02.51.10.01.09.16.09.00, avc1.64002A…).
     */
    public static VideoCodec desdeCadena(String vcodec) {
        if (vcodec == null || vcodec.isBlank() || "none".equalsIgnoreCase(vcodec)) {
            return NINGUNO;
        }
        String c = vcodec.toLowerCase(java.util.Locale.ROOT);
        if (c.startsWith("av01") || c.startsWith("av1")) {
            return AV1;
        }
        if (c.startsWith("vp09") || c.startsWith("vp9")) {
            return VP9;
        }
        if (c.startsWith("hev1") || c.startsWith("hvc1") || c.startsWith("h265") || c.startsWith("hevc")) {
            return H265;
        }
        if (c.startsWith("avc1") || c.startsWith("avc3") || c.startsWith("h264") || c.startsWith("h.264")) {
            return H264;
        }
        if (c.startsWith("vp08") || c.startsWith("vp8")) {
            return VP8;
        }
        return DESCONOCIDO;
    }
}
