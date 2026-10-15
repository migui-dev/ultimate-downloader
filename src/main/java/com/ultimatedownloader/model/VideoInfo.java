package com.ultimatedownloader.model;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Metadatos de un vídeo tal como los devuelve «yt-dlp -J», ya normalizados. */
public record VideoInfo(
        String id,
        String titulo,
        String canal,
        Duration duracion,
        String miniaturaUrl,
        String urlPagina,
        boolean enDirecto,
        boolean tieneDrm,
        int limiteEdad,
        String disponibilidad,
        List<VideoFormat> formatosVideo,
        List<AudioFormat> formatosAudio,
        List<SubtitleTrack> subtitulos,
        List<SubtitleTrack> subtitulosAutomaticos,
        String tituloLista,
        Integer videosEnLista,
        java.time.LocalDate fechaSubida) {

    public VideoInfo {
        formatosVideo = List.copyOf(formatosVideo);
        formatosAudio = List.copyOf(formatosAudio);
        subtitulos = List.copyOf(subtitulos);
        subtitulosAutomaticos = List.copyOf(subtitulosAutomaticos);
    }

    /** true si el enlace analizado pertenece a una lista de reproducción. */
    public boolean perteneceALista() {
        return videosEnLista != null && videosEnLista > 1;
    }

    /** true si hay varias pistas de audio en idiomas distintos. */
    public boolean tieneVariosIdiomasDeAudio() {
        return formatosAudio.stream()
                .map(AudioFormat::idioma)
                .filter(i -> i != null && !i.isBlank())
                .distinct()
                .count() > 1;
    }

    public Optional<VideoFormat> mejorVideo() {
        return formatosVideo.stream().findFirst();
    }

    public Optional<AudioFormat> mejorAudio() {
        return formatosAudio.stream().findFirst();
    }

    public String duracionTexto() {
        long total = duracion.getSeconds();
        if (total <= 0) {
            return enDirecto ? "En directo" : "—";
        }
        long horas = total / 3600;
        long minutos = (total % 3600) / 60;
        long segundos = total % 60;
        return horas > 0
                ? String.format("%d:%02d:%02d", horas, minutos, segundos)
                : String.format("%d:%02d", minutos, segundos);
    }
}
