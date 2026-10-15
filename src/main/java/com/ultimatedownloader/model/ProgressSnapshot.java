package com.ultimatedownloader.model;

/**
 * Foto fija del progreso de una descarga, sacada de una línea de yt-dlp. La fracción es
 * negativa y los bytes van a 0 cuando yt-dlp no los da.
 */
public record ProgressSnapshot(
        DownloadPhase fase,
        double fraccion,
        long bytesDescargados,
        long bytesTotales,
        double velocidadBytesPorSeg,
        Long segundosRestantes) {

    public static ProgressSnapshot deFase(DownloadPhase fase) {
        return new ProgressSnapshot(fase, -1, 0, 0, 0, null);
    }

    public boolean progresoConocido() {
        return fraccion >= 0;
    }
}
