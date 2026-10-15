package com.ultimatedownloader.service.convert;

import com.ultimatedownloader.model.calidad.FormatoSalida;
import com.ultimatedownloader.model.convert.MediaInfo;
import com.ultimatedownloader.model.convert.PistaMedia;
import com.ultimatedownloader.model.convert.TipoPista;
import com.ultimatedownloader.util.Textos;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decide si se puede cambiar de contenedor copiando las pistas tal cual. Remuxear siempre es
 * mejor: sale idéntico al original y tarda segundos. Solo se puede si el contenedor de destino
 * admite los códecs que ya tiene el archivo, y eso es lo que resuelve esta tabla.
 */
public final class RemuxDecider {

    /** Códecs de vídeo que admite cada contenedor, con los nombres que usa ffprobe. */
    private static final Map<FormatoSalida, Set<String>> VIDEO = Map.of(
            FormatoSalida.MKV, Set.of("h264", "hevc", "av1", "vp9", "vp8", "mpeg4", "mpeg2video",
                    "theora", "ffv1", "prores", "huffyuv", "dnxhd"),
            FormatoSalida.MP4, Set.of("h264", "hevc", "av1", "vp9", "mpeg4", "mpeg2video"),
            FormatoSalida.MOV, Set.of("h264", "hevc", "prores", "mpeg4", "dnxhd", "mjpeg"),
            FormatoSalida.WEBM, Set.of("vp8", "vp9", "av1"),
            FormatoSalida.AVI, Set.of("mpeg4", "mjpeg", "ffv1", "huffyuv", "h264"));

    /** Códecs de audio que admite cada contenedor. */
    private static final Map<FormatoSalida, Set<String>> AUDIO = Map.ofEntries(
            Map.entry(FormatoSalida.MKV, Set.of("aac", "mp3", "opus", "vorbis", "flac", "alac",
                    "ac3", "eac3", "dts", "pcm_s16le", "pcm_s24le", "pcm_f32le", "truehd")),
            Map.entry(FormatoSalida.MP4, Set.of("aac", "mp3", "alac", "ac3", "eac3")),
            Map.entry(FormatoSalida.MOV, Set.of("aac", "alac", "mp3", "pcm_s16le", "pcm_s24le",
                    "pcm_f32le")),
            Map.entry(FormatoSalida.WEBM, Set.of("opus", "vorbis")),
            Map.entry(FormatoSalida.AVI, Set.of("mp3", "ac3", "pcm_s16le")),
            // Contenedores de solo audio.
            Map.entry(FormatoSalida.M4A, Set.of("aac", "alac")),
            Map.entry(FormatoSalida.MP3, Set.of("mp3")),
            Map.entry(FormatoSalida.FLAC, Set.of("flac")),
            Map.entry(FormatoSalida.WAV, Set.of("pcm_s16le", "pcm_s24le", "pcm_s32le",
                    "pcm_f32le", "pcm_u8")),
            Map.entry(FormatoSalida.AIFF, Set.of("pcm_s16be", "pcm_s24be", "pcm_s32be",
                    "pcm_f32be")),
            Map.entry(FormatoSalida.OPUS, Set.of("opus")),
            Map.entry(FormatoSalida.OGG, Set.of("vorbis", "opus", "flac")));

    private RemuxDecider() {
    }

    /** Si se puede copiar sin recomprimir, y si no, por qué. */
    public record Resultado(boolean posible, String motivo) {

        public static final Resultado SI = new Resultado(true, null);

        public static Resultado no(String motivo) {
            return new Resultado(false, motivo);
        }
    }

    /** ¿Se puede pasar este archivo al formato de destino copiando las pistas? */
    public static Resultado comprobar(MediaInfo info, FormatoSalida destino, boolean soloAudio) {
        if (info == null || destino == null) {
            return Resultado.no(Textos.t("remux.falta_informacion"));
        }

        Optional<PistaMedia> audio = info.primeraDeAudio();
        if (audio.isEmpty()) {
            return Resultado.no(Textos.t("remux.sin_pista_de_audio"));
        }
        if (!admiteAudio(destino, audio.get().codec())) {
            return Resultado.no(Textos.t("remux.no_admite_audio", destino.nombreLegible(),
                    nombreLegible(audio.get().codec())));
        }

        if (soloAudio || destino.esAudio()) {
            return Resultado.SI;
        }

        Optional<PistaMedia> video = info.primeraDeVideo();
        if (video.isEmpty()) {
            return Resultado.no(Textos.t("remux.sin_imagen"));
        }
        if (!admiteVideo(destino, video.get().codec())) {
            return Resultado.no(Textos.t("remux.no_admite_video", destino.nombreLegible(),
                    nombreLegible(video.get().codec())));
        }
        return Resultado.SI;
    }

    /** Contenedores a los que este archivo puede pasar sin recodificar nada. */
    public static List<FormatoSalida> destinosSinRecodificar(MediaInfo info) {
        List<FormatoSalida> candidatos = info != null && info.tieneVideo()
                ? FormatoSalida.deVideo()
                : FormatoSalida.deAudio();
        return candidatos.stream()
                .filter(f -> comprobar(info, f, !info.tieneVideo()).posible())
                .toList();
    }

    public static boolean admiteVideo(FormatoSalida formato, String codec) {
        Set<String> admitidos = VIDEO.get(formato);
        return admitidos != null && codec != null
                && admitidos.contains(codec.toLowerCase(Locale.ROOT));
    }

    public static boolean admiteAudio(FormatoSalida formato, String codec) {
        Set<String> admitidos = AUDIO.get(formato);
        return admitidos != null && codec != null
                && admitidos.contains(codec.toLowerCase(Locale.ROOT));
    }

    /** Nombre del códec tal como se le enseña al usuario. */
    public static String nombreLegible(String codec) {
        if (codec == null) {
            return Textos.t("comun.desconocido_minuscula");
        }
        return switch (codec.toLowerCase(Locale.ROOT)) {
            case "h264" -> "H.264";
            case "hevc" -> "H.265";
            case "av1" -> "AV1";
            case "vp9" -> "VP9";
            case "vp8" -> "VP8";
            case "mpeg4" -> "MPEG-4";
            case "aac" -> "AAC";
            case "mp3" -> "MP3";
            case "opus" -> "Opus";
            case "vorbis" -> "Vorbis";
            case "flac" -> "FLAC";
            case "alac" -> "ALAC";
            case "ac3" -> "AC-3";
            case "eac3" -> "E-AC-3";
            case "ffv1" -> "FFV1";
            case "prores" -> "ProRes";
            default -> codec.startsWith("pcm_") ? "PCM" : codec.toUpperCase(Locale.ROOT);
        };
    }

    /** Pistas que se van a copiar; sirve para contárselo al usuario. */
    public static List<PistaMedia> pistasQueSeCopian(MediaInfo info, boolean soloAudio) {
        return info.pistas().stream()
                .filter(p -> p.tipo() == TipoPista.AUDIO
                        || (!soloAudio && p.tipo() == TipoPista.VIDEO))
                .toList();
    }
}
