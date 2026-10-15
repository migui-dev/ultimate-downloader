package com.ultimatedownloader.model;

import com.ultimatedownloader.util.Textos;
import java.util.EnumSet;
import java.util.Set;

/** Contenedores de salida para vídeo y qué códecs admite cada uno sin recomprimir. */
public enum VideoContainer {

    /** Matroska lo admite todo: es la opción segura para remuxear sin recomprimir. */
    MKV("mkv", "MKV (Matroska)",
            EnumSet.of(VideoCodec.AV1, VideoCodec.VP9, VideoCodec.H264, VideoCodec.H265, VideoCodec.VP8),
            EnumSet.of(AudioCodec.OPUS, AudioCodec.AAC, AudioCodec.VORBIS, AudioCodec.MP3,
                    AudioCodec.FLAC, AudioCodec.AC3, AudioCodec.EAC3, AudioCodec.PCM)),

    /** MP4 es el más compatible con reproductores y editores, pero no acepta Opus ni VP8. */
    MP4("mp4", "MP4",
            EnumSet.of(VideoCodec.AV1, VideoCodec.VP9, VideoCodec.H264, VideoCodec.H265),
            EnumSet.of(AudioCodec.AAC, AudioCodec.MP3, AudioCodec.AC3, AudioCodec.EAC3)),

    /** WebM solo acepta la familia VPx/AV1 con Opus o Vorbis. */
    WEBM("webm", "WebM",
            EnumSet.of(VideoCodec.AV1, VideoCodec.VP9, VideoCodec.VP8),
            EnumSet.of(AudioCodec.OPUS, AudioCodec.VORBIS));

    private final String extension;
    private final String nombreLegible;
    private final Set<VideoCodec> codecsVideo;
    private final Set<AudioCodec> codecsAudio;

    VideoContainer(String extension, String nombreLegible,
                   Set<VideoCodec> codecsVideo, Set<AudioCodec> codecsAudio) {
        this.extension = extension;
        this.nombreLegible = nombreLegible;
        this.codecsVideo = codecsVideo;
        this.codecsAudio = codecsAudio;
    }

    public String extension() {
        return extension;
    }

    /** Nombre para la interfaz; se traduce si el idioma elegido lo traduce. */
    public String nombreLegible() {
        return Textos.deEnum("contenedor", name(), nombreLegible);
    }

    public boolean admite(VideoCodec codec) {
        return codec == VideoCodec.NINGUNO || codec == VideoCodec.DESCONOCIDO || codecsVideo.contains(codec);
    }

    public boolean admite(AudioCodec codec) {
        return codec == AudioCodec.NINGUNO || codec == AudioCodec.DESCONOCIDO || codecsAudio.contains(codec);
    }

    @Override
    public String toString() {
        return nombreLegible;
    }
}
