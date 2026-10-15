package com.ultimatedownloader.model.calidad;

import com.ultimatedownloader.util.Textos;
import java.util.List;
import java.util.Map;

/**
 * La tabla de calidades: el único sitio donde se decide con qué parámetros se codifica cada
 * cosa. Descargas, conversión y separación salen todas de aquí.
 *
 * Ojo con dos cosas: FLAC no admite coma flotante de 32 bits, su máximo son 24 bits enteros;
 * y remuxear, cuando se puede, es mejor que cualquier perfil de aquí.
 */
public final class Calidades {

    /** Frecuencia de muestreo de referencia para estimar tamaños cuando no se sabe la real. */
    private static final int FRECUENCIA_REFERENCIA = 48000;

    private Calidades() {
    }

    // ------------------------------------------------------------------ audio

    private static final Map<FormatoSalida, Map<NivelCalidad, PerfilCalidad>> AUDIO = Map.of(

            FormatoSalida.WAV, Map.of(
                    NivelCalidad.MAXIMA, perfil(FormatoSalida.WAV, NivelCalidad.MAXIMA,
                            List.of("-c:a", "pcm_f32le"),
                            Textos.t("calidad.wav_maxima"), 3072, true),
                    NivelCalidad.MEDIA, perfil(FormatoSalida.WAV, NivelCalidad.MEDIA,
                            List.of("-c:a", "pcm_s24le"),
                            Textos.t("calidad.24_bits_sin_perdida"), 2304, true),
                    NivelCalidad.MINIMA, perfil(FormatoSalida.WAV, NivelCalidad.MINIMA,
                            List.of("-c:a", "pcm_s16le"),
                            Textos.t("calidad.wav_minima"), 1536, true)),

            FormatoSalida.AIFF, Map.of(
                    NivelCalidad.MAXIMA, perfil(FormatoSalida.AIFF, NivelCalidad.MAXIMA,
                            List.of("-c:a", "pcm_f32be"),
                            Textos.t("calidad.wav_maxima"), 3072, true),
                    NivelCalidad.MEDIA, perfil(FormatoSalida.AIFF, NivelCalidad.MEDIA,
                            List.of("-c:a", "pcm_s24be"),
                            Textos.t("calidad.24_bits_sin_perdida"), 2304, true),
                    NivelCalidad.MINIMA, perfil(FormatoSalida.AIFF, NivelCalidad.MINIMA,
                            List.of("-c:a", "pcm_s16be"),
                            Textos.t("calidad.wav_minima"), 1536, true)),

            FormatoSalida.FLAC, Map.of(
                    // FLAC no guarda coma flotante: 24 bits enteros es su máximo práctico.
                    NivelCalidad.MAXIMA, perfil(FormatoSalida.FLAC, NivelCalidad.MAXIMA,
                            List.of("-c:a", "flac", "-compression_level", "12",
                                    "-sample_fmt", "s32"),
                            Textos.t("calidad.flac_maxima"), 1100, true),
                    NivelCalidad.MEDIA, perfil(FormatoSalida.FLAC, NivelCalidad.MEDIA,
                            List.of("-c:a", "flac", "-compression_level", "8",
                                    "-sample_fmt", "s32"),
                            Textos.t("calidad.24_bits_sin_perdida"), 1100, true),
                    NivelCalidad.MINIMA, perfil(FormatoSalida.FLAC, NivelCalidad.MINIMA,
                            List.of("-c:a", "flac", "-compression_level", "5",
                                    "-sample_fmt", "s16"),
                            Textos.t("calidad.flac_minima"), 700, true)),

            FormatoSalida.MP3, Map.of(
                    NivelCalidad.MAXIMA, perfil(FormatoSalida.MP3, NivelCalidad.MAXIMA,
                            List.of("-c:a", "libmp3lame", "-b:a", "320k",
                                    "-compression_level", "0"),
                            Textos.t("calidad.mp3_maxima"), 320, false),
                    NivelCalidad.MEDIA, perfil(FormatoSalida.MP3, NivelCalidad.MEDIA,
                            List.of("-c:a", "libmp3lame", "-q:a", "0"),
                            Textos.t("calidad.mp3_media"), 245, false),
                    NivelCalidad.MINIMA, perfil(FormatoSalida.MP3, NivelCalidad.MINIMA,
                            List.of("-c:a", "libmp3lame", "-b:a", "128k"),
                            "128 kbps", 128, false)),

            FormatoSalida.M4A, Map.of(
                    NivelCalidad.MAXIMA, perfil(FormatoSalida.M4A, NivelCalidad.MAXIMA,
                            List.of("-c:a", "aac", "-b:a", "320k"),
                            Textos.t("calidad.aac_maxima"), 320, false),
                    NivelCalidad.MEDIA, perfil(FormatoSalida.M4A, NivelCalidad.MEDIA,
                            List.of("-c:a", "aac", "-b:a", "192k"),
                            Textos.t("calidad.aac_media"), 192, false),
                    NivelCalidad.MINIMA, perfil(FormatoSalida.M4A, NivelCalidad.MINIMA,
                            List.of("-c:a", "aac", "-b:a", "96k"),
                            Textos.t("calidad.aac_minima"), 96, false)),

            FormatoSalida.OPUS, Map.of(
                    NivelCalidad.MAXIMA, perfil(FormatoSalida.OPUS, NivelCalidad.MAXIMA,
                            List.of("-c:a", "libopus", "-b:a", "510k", "-vbr", "on",
                                    "-compression_level", "10"),
                            Textos.t("calidad.opus_maxima"), 510, false),
                    NivelCalidad.MEDIA, perfil(FormatoSalida.OPUS, NivelCalidad.MEDIA,
                            List.of("-c:a", "libopus", "-b:a", "160k", "-vbr", "on",
                                    "-compression_level", "10"),
                            "160 kbps, transparente para casi todo", 160, false),
                    NivelCalidad.MINIMA, perfil(FormatoSalida.OPUS, NivelCalidad.MINIMA,
                            List.of("-c:a", "libopus", "-b:a", "64k", "-vbr", "on"),
                            "64 kbps", 64, false)),

            FormatoSalida.OGG, Map.of(
                    NivelCalidad.MAXIMA, perfil(FormatoSalida.OGG, NivelCalidad.MAXIMA,
                            List.of("-c:a", "libvorbis", "-q:a", "10"),
                            Textos.t("calidad.vorbis_maxima"), 500, false),
                    NivelCalidad.MEDIA, perfil(FormatoSalida.OGG, NivelCalidad.MEDIA,
                            List.of("-c:a", "libvorbis", "-q:a", "5"),
                            Textos.t("calidad.vorbis_media"), 160, false),
                    NivelCalidad.MINIMA, perfil(FormatoSalida.OGG, NivelCalidad.MINIMA,
                            List.of("-c:a", "libvorbis", "-q:a", "2"),
                            Textos.t("calidad.vorbis_minima"), 96, false)));

    // ------------------------------------------------------------------ vídeo

    /**
     * Audio que acompaña a cada nivel al recodificar un vídeo. Depende del contenedor: en
     * máxima se usa el códec sin pérdida que admita cada uno, y en WebM, que no tiene ninguno,
     * el Opus más alto.
     */
    private static final Map<FormatoSalida, Map<NivelCalidad, List<String>>> AUDIO_DE_VIDEO =
            Map.of(
                    FormatoSalida.MKV, Map.of(
                            NivelCalidad.MAXIMA, List.of("-c:a", "flac", "-compression_level", "12"),
                            NivelCalidad.MEDIA, List.of("-c:a", "aac", "-b:a", "192k"),
                            NivelCalidad.MINIMA, List.of("-c:a", "aac", "-b:a", "96k")),

                    FormatoSalida.MP4, Map.of(
                            NivelCalidad.MAXIMA, List.of("-c:a", "alac"),
                            NivelCalidad.MEDIA, List.of("-c:a", "aac", "-b:a", "192k"),
                            NivelCalidad.MINIMA, List.of("-c:a", "aac", "-b:a", "96k")),

                    FormatoSalida.MOV, Map.of(
                            NivelCalidad.MAXIMA, List.of("-c:a", "pcm_s24le"),
                            NivelCalidad.MEDIA, List.of("-c:a", "aac", "-b:a", "192k"),
                            NivelCalidad.MINIMA, List.of("-c:a", "aac", "-b:a", "96k")),

                    FormatoSalida.WEBM, Map.of(
                            NivelCalidad.MAXIMA, List.of("-c:a", "libopus", "-b:a", "510k",
                                    "-vbr", "on"),
                            NivelCalidad.MEDIA, List.of("-c:a", "libopus", "-b:a", "160k",
                                    "-vbr", "on"),
                            NivelCalidad.MINIMA, List.of("-c:a", "libopus", "-b:a", "64k",
                                    "-vbr", "on")),

                    FormatoSalida.AVI, Map.of(
                            NivelCalidad.MAXIMA, List.of("-c:a", "pcm_s16le"),
                            NivelCalidad.MEDIA, List.of("-c:a", "libmp3lame", "-q:a", "0"),
                            NivelCalidad.MINIMA, List.of("-c:a", "libmp3lame", "-b:a", "128k")));

    private static final Map<FormatoSalida, Map<NivelCalidad, PerfilCalidad>> VIDEO = Map.of(

            // Matroska lo admite todo: en máxima va FFV1, que es sin pérdida de verdad.
            FormatoSalida.MKV, Map.of(
                    NivelCalidad.MAXIMA, perfilVideo(FormatoSalida.MKV, NivelCalidad.MAXIMA,
                            List.of("-c:v", "ffv1", "-level", "3", "-coder", "1", "-context", "1",
                                    "-g", "1", "-slices", "24", "-slicecrc", "1"),
                            Textos.t("calidad.ffv1_completo"), 0, true),
                    NivelCalidad.MEDIA, perfilVideo(FormatoSalida.MKV, NivelCalidad.MEDIA,
                            List.of("-c:v", "libx264", "-preset", "slow", "-crf", "18",
                                    "-pix_fmt", "yuv420p"),
                            Textos.t("calidad.h264_crf18_lento"), 0, false),
                    NivelCalidad.MINIMA, perfilVideo(FormatoSalida.MKV, NivelCalidad.MINIMA,
                            List.of("-c:v", "libx264", "-preset", "medium", "-crf", "28",
                                    "-pix_fmt", "yuv420p"),
                            Textos.t("calidad.h264_crf28"), 0, false)),

            // MP4 no admite FFV1: su «sin pérdida» es H.264 con CRF 0.
            FormatoSalida.MP4, Map.of(
                    NivelCalidad.MAXIMA, perfilVideo(FormatoSalida.MP4, NivelCalidad.MAXIMA,
                            List.of("-c:v", "libx264", "-preset", "veryslow", "-crf", "0",
                                    "-movflags", "+faststart"),
                            Textos.t("calidad.h264_sin_perdida"), 0, true),
                    NivelCalidad.MEDIA, perfilVideo(FormatoSalida.MP4, NivelCalidad.MEDIA,
                            List.of("-c:v", "libx264", "-preset", "slow", "-crf", "18",
                                    "-pix_fmt", "yuv420p", "-movflags", "+faststart"),
                            Textos.t("calidad.h264_crf18_lento"), 0, false),
                    NivelCalidad.MINIMA, perfilVideo(FormatoSalida.MP4, NivelCalidad.MINIMA,
                            List.of("-c:v", "libx264", "-preset", "medium", "-crf", "28",
                                    "-pix_fmt", "yuv420p", "-movflags", "+faststart"),
                            Textos.t("calidad.h264_crf28"), 0, false)),

            // MOV es el contenedor natural de ProRes, que es lo que esperan los editores.
            FormatoSalida.MOV, Map.of(
                    NivelCalidad.MAXIMA, perfilVideo(FormatoSalida.MOV, NivelCalidad.MAXIMA,
                            List.of("-c:v", "prores_ks", "-profile:v", "5",
                                    "-pix_fmt", "yuva444p10le", "-vendor", "apl0"),
                            Textos.t("calidad.prores_xq"), 0, true),
                    NivelCalidad.MEDIA, perfilVideo(FormatoSalida.MOV, NivelCalidad.MEDIA,
                            List.of("-c:v", "prores_ks", "-profile:v", "3",
                                    "-pix_fmt", "yuv422p10le", "-vendor", "apl0"),
                            Textos.t("calidad.prores_hq"), 0, false),
                    NivelCalidad.MINIMA, perfilVideo(FormatoSalida.MOV, NivelCalidad.MINIMA,
                            List.of("-c:v", "libx264", "-preset", "medium", "-crf", "28",
                                    "-pix_fmt", "yuv420p"),
                            Textos.t("calidad.h264_crf28"), 0, false)),

            // WebM solo admite la familia VPx/AV1.
            FormatoSalida.WEBM, Map.of(
                    NivelCalidad.MAXIMA, perfilVideo(FormatoSalida.WEBM, NivelCalidad.MAXIMA,
                            List.of("-c:v", "libvpx-vp9", "-lossless", "1", "-row-mt", "1"),
                            Textos.t("calidad.vp9_sin_perdida"), 0, true),
                    NivelCalidad.MEDIA, perfilVideo(FormatoSalida.WEBM, NivelCalidad.MEDIA,
                            List.of("-c:v", "libvpx-vp9", "-crf", "30", "-b:v", "0",
                                    "-row-mt", "1"),
                            Textos.t("calidad.vp9_crf30"), 0, false),
                    NivelCalidad.MINIMA, perfilVideo(FormatoSalida.WEBM, NivelCalidad.MINIMA,
                            List.of("-c:v", "libvpx-vp9", "-crf", "40", "-b:v", "0",
                                    "-row-mt", "1"),
                            Textos.t("calidad.vp9_crf40"), 0, false)),

            // AVI es un contenedor antiguo; con FFV1 sigue valiendo para archivar.
            FormatoSalida.AVI, Map.of(
                    NivelCalidad.MAXIMA, perfilVideo(FormatoSalida.AVI, NivelCalidad.MAXIMA,
                            List.of("-c:v", "ffv1", "-level", "3", "-coder", "1", "-context", "1",
                                    "-g", "1"),
                            Textos.t("calidad.ffv1"), 0, true),
                    NivelCalidad.MEDIA, perfilVideo(FormatoSalida.AVI, NivelCalidad.MEDIA,
                            List.of("-c:v", "libx264", "-preset", "slow", "-crf", "18",
                                    "-pix_fmt", "yuv420p"),
                            Textos.t("calidad.h264_crf18"), 0, false),
                    NivelCalidad.MINIMA, perfilVideo(FormatoSalida.AVI, NivelCalidad.MINIMA,
                            List.of("-c:v", "libx264", "-preset", "medium", "-crf", "28",
                                    "-pix_fmt", "yuv420p"),
                            Textos.t("calidad.h264_crf28"), 0, false)));

    // ------------------------------------------------------------------ consultas

    /** Perfil exacto de un formato con un nivel. Nunca devuelve null. */
    public static PerfilCalidad de(FormatoSalida formato, NivelCalidad nivel) {
        if (formato == null || nivel == null) {
            throw new IllegalArgumentException("Hace falta formato y nivel para buscar el perfil");
        }
        Map<NivelCalidad, PerfilCalidad> porNivel = formato.esAudio()
                ? AUDIO.get(formato)
                : VIDEO.get(formato);
        if (porNivel == null) {
            throw new IllegalArgumentException("Formato sin perfiles: " + formato);
        }
        PerfilCalidad perfil = porNivel.get(nivel);
        if (perfil == null) {
            throw new IllegalArgumentException("Nivel sin perfil: " + formato + " / " + nivel);
        }
        return perfil;
    }

    /** Tamaño estimado de un audio con ese perfil. Con frecuencia 0 se dan por hechos 48 kHz. */
    public static long tamanoEstimado(PerfilCalidad perfil, long segundos, int canales,
                                      int frecuencia) {
        if (segundos <= 0 || perfil.bitrateKbps() <= 0) {
            return 0;
        }
        double bits = perfil.bitrateKbps() * 1000.0;
        if (perfil.sinPerdida()) {
            // En PCM el bitrate de la tabla es para estéreo a 48 kHz; se ajusta al real.
            double factorCanales = canales > 0 ? canales / 2.0 : 1;
            double factorFrecuencia = frecuencia > 0 ? (double) frecuencia / FRECUENCIA_REFERENCIA : 1;
            bits *= factorCanales * factorFrecuencia;
        }
        return (long) (bits / 8.0 * segundos);
    }

    /**
     * Variante por hardware del perfil de vídeo, con NVENC. Es mucho más rápida, pero a igual
     * tamaño el software da mejor calidad; por eso no es la opción por defecto.
     */
    public static PerfilCalidad conNvenc(PerfilCalidad perfil) {
        if (perfil.formato().esAudio() || perfil.nivel() == NivelCalidad.MAXIMA
                || !admiteNvenc(perfil.formato())) {
            // En máxima manda la calidad, y ahí gana el software; en WebM no hay NVENC de VP9.
            return perfil;
        }
        List<String> video = perfil.nivel() == NivelCalidad.MEDIA
                ? List.of("-c:v", "h264_nvenc", "-preset", "p7", "-tune", "hq",
                        "-rc", "vbr", "-cq", "19", "-b:v", "0", "-pix_fmt", "yuv420p")
                : List.of("-c:v", "h264_nvenc", "-preset", "p5", "-tune", "hq",
                        "-rc", "vbr", "-cq", "28", "-b:v", "0", "-pix_fmt", "yuv420p");

        return new PerfilCalidad(perfil.formato(), perfil.nivel(), video,
                perfil.argumentosAudio(), Textos.t("calidad.codificado_por_la_gpu", perfil.resumen()),
                perfil.bitrateKbps(), false);
    }

    /** true si tiene sentido ofrecer NVENC para ese contenedor. */
    public static boolean admiteNvenc(FormatoSalida formato) {
        return formato == FormatoSalida.MP4 || formato == FormatoSalida.MKV
                || formato == FormatoSalida.MOV;
    }

    private static List<String> audioDe(FormatoSalida formato, NivelCalidad nivel) {
        return AUDIO_DE_VIDEO.get(formato).get(nivel);
    }

    /** En los formatos de audio no hay parte de vídeo. */
    private static PerfilCalidad perfil(FormatoSalida formato, NivelCalidad nivel,
                                        List<String> argumentos, String resumen,
                                        int bitrateKbps, boolean sinPerdida) {
        return new PerfilCalidad(formato, nivel, List.of(), argumentos, resumen,
                bitrateKbps, sinPerdida);
    }

    /** Los perfiles de vídeo llevan además el audio que le corresponde a ese contenedor. */
    private static PerfilCalidad perfilVideo(FormatoSalida formato, NivelCalidad nivel,
                                             List<String> video, String resumen,
                                             int bitrateKbps, boolean sinPerdida) {
        return new PerfilCalidad(formato, nivel, video, audioDe(formato, nivel),
                resumen, bitrateKbps, sinPerdida);
    }
}
