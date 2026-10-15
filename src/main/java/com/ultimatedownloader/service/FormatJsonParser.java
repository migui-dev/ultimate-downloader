package com.ultimatedownloader.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultimatedownloader.model.AudioCodec;
import com.ultimatedownloader.model.AudioFormat;
import com.ultimatedownloader.model.SubtitleTrack;
import com.ultimatedownloader.model.VideoCodec;
import com.ultimatedownloader.model.VideoFormat;
import com.ultimatedownloader.model.VideoInfo;
import com.ultimatedownloader.util.Idiomas;
import com.ultimatedownloader.util.Textos;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.StreamSupport;

/**
 * Convierte el JSON de «yt-dlp -J» en VideoInfo. El JSON de YouTube es irregular: formatos sin
 * tamaño, campos ausentes, nulos donde iría un número y pistas que no son ni vídeo ni audio.
 * Todo eso se filtra aquí para que el resto trabaje con datos limpios.
 */
public final class FormatJsonParser {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Analiza el JSON completo que escribe yt-dlp en la salida estándar. */
    public VideoInfo analizar(String json) throws JsonProcessingException {
        return analizar(mapper.readTree(json));
    }

    /** Analiza un árbol ya leído; útil para los tests. */
    public VideoInfo analizar(JsonNode raiz) {
        JsonNode video = raiz;
        String tituloLista = null;
        Integer videosEnLista = null;

        // Un enlace de lista (o una búsqueda) devuelve _type=playlist con los vídeos en entries.
        if ("playlist".equals(texto(raiz, "_type"))) {
            JsonNode entradas = raiz.get("entries");
            if (entradas == null || !entradas.isArray() || entradas.isEmpty()) {
                throw new IllegalArgumentException("La lista de reproducción no contiene vídeos");
            }
            video = entradas.get(0);
            tituloLista = texto(raiz, "title");
            videosEnLista = entero(raiz, "playlist_count");
            if (videosEnLista == null) {
                videosEnLista = entradas.size();
            }
        } else {
            tituloLista = texto(raiz, "playlist");
            videosEnLista = primerNoNulo(entero(raiz, "playlist_count"), entero(raiz, "n_entries"));
        }

        List<VideoFormat> formatosVideo = new ArrayList<>();
        List<AudioFormat> formatosAudio = new ArrayList<>();
        boolean variosIdiomas = cuentaIdiomas(video) > 1;

        JsonNode formatos = video.get("formats");
        if (formatos != null && formatos.isArray()) {
            for (JsonNode f : formatos) {
                if (esDescartable(f)) {
                    continue;
                }
                if (esSoloAudio(f)) {
                    formatosAudio.add(leerAudio(f, variosIdiomas));
                } else if (tieneVideo(f)) {
                    formatosVideo.add(leerVideo(f));
                }
            }
        }

        formatosVideo.sort(VideoFormat.MEJOR_PRIMERO);
        formatosAudio.sort(AudioFormat.MEJOR_PRIMERO);

        String estadoDirecto = texto(video, "live_status");
        boolean enDirecto = booleano(video, "is_live")
                || "is_live".equals(estadoDirecto)
                || "is_upcoming".equals(estadoDirecto);

        return new VideoInfo(
                primerNoNulo(texto(video, "id"), ""),
                primerNoNulo(texto(video, "title"), texto(video, "fulltitle"), Textos.t("comun.video_sin_titulo")),
                primerNoNulo(texto(video, "channel"), texto(video, "uploader"),
                        texto(video, "creator"), Textos.t("comun.canal_desconocido")),
                Duration.ofSeconds(Math.max(0, Math.round(decimal(video, "duration", 0)))),
                mejorMiniatura(video),
                primerNoNulo(texto(video, "webpage_url"), texto(video, "original_url"), ""),
                enDirecto,
                booleano(video, "_has_drm"),
                primerNoNulo(entero(video, "age_limit"), 0),
                texto(video, "availability"),
                formatosVideo,
                formatosAudio,
                leerSubtitulos(video.get("subtitles"), false),
                leerSubtitulos(video.get("automatic_captions"), true),
                tituloLista,
                videosEnLista,
                fechaSubida(video));
    }

    // ------------------------------------------------------------------ formatos

    /** Guiones gráficos y otras pistas que no sirven para descargar. */
    private boolean esDescartable(JsonNode f) {
        String ext = texto(f, "ext");
        String protocolo = texto(f, "protocol");
        String nota = texto(f, "format_note");
        return "mhtml".equalsIgnoreCase(ext)
                || "mhtml".equalsIgnoreCase(protocolo)
                || (nota != null && nota.toLowerCase(Locale.ROOT).contains("storyboard"))
                || texto(f, "format_id") == null;
    }

    private boolean esSoloAudio(JsonNode f) {
        VideoCodec video = VideoCodec.desdeCadena(texto(f, "vcodec"));
        if (video != VideoCodec.NINGUNO) {
            return false;
        }
        // Los formatos HLS de audio de YouTube llegan sin acodec, pero con resolution=audio only.
        return "audio only".equalsIgnoreCase(texto(f, "resolution"))
                || AudioCodec.desdeCadena(texto(f, "acodec")) != AudioCodec.NINGUNO
                || "none".equalsIgnoreCase(primerNoNulo(texto(f, "video_ext"), ""));
    }

    private boolean tieneVideo(JsonNode f) {
        return VideoCodec.desdeCadena(texto(f, "vcodec")) != VideoCodec.NINGUNO;
    }

    private VideoFormat leerVideo(JsonNode f) {
        Long exacto = largo(f, "filesize");
        Long aproximado = largo(f, "filesize_approx");
        double vbr = decimal(f, "vbr", 0);
        double tbr = decimal(f, "tbr", 0);

        return new VideoFormat(
                texto(f, "format_id"),
                primerNoNulo(entero(f, "width"), 0),
                primerNoNulo(entero(f, "height"), 0),
                decimal(f, "fps", 0),
                VideoCodec.desdeCadena(texto(f, "vcodec")),
                texto(f, "vcodec"),
                texto(f, "dynamic_range"),
                vbr > 0 ? vbr : tbr,
                texto(f, "ext"),
                texto(f, "protocol"),
                exacto != null ? exacto : aproximado,
                exacto != null,
                texto(f, "format_note"),
                AudioCodec.desdeCadena(texto(f, "acodec")));
    }

    private AudioFormat leerAudio(JsonNode f, boolean variosIdiomas) {
        Long exacto = largo(f, "filesize");
        Long aproximado = largo(f, "filesize_approx");
        double abr = decimal(f, "abr", 0);
        double tbr = decimal(f, "tbr", 0);
        String idioma = texto(f, "language");
        String nota = texto(f, "format_note");

        return new AudioFormat(
                texto(f, "format_id"),
                AudioCodec.desdeCadena(texto(f, "acodec")),
                texto(f, "acodec"),
                abr > 0 ? abr : tbr,
                entero(f, "asr"),
                entero(f, "audio_channels"),
                idioma,
                variosIdiomas ? Idiomas.nombre(idioma) : null,
                esPistaOriginal(nota),
                texto(f, "ext"),
                texto(f, "protocol"),
                exacto != null ? exacto : aproximado,
                exacto != null,
                nota);
    }

    /** yt-dlp marca la pista original con una nota tipo «English (US) original (default), low». */
    private boolean esPistaOriginal(String nota) {
        return nota != null && nota.toLowerCase(Locale.ROOT).contains("original");
    }

    private long cuentaIdiomas(JsonNode video) {
        JsonNode formatos = video.get("formats");
        if (formatos == null || !formatos.isArray()) {
            return 0;
        }
        return StreamSupport.stream(formatos.spliterator(), false)
                .filter(this::esSoloAudio)
                .map(f -> texto(f, "language"))
                .filter(i -> i != null && !i.isBlank())
                .distinct()
                .count();
    }

    // ------------------------------------------------------------------ metadatos

    /**
     * YouTube suele dar la miniatura principal en WebP y JavaFX no sabe leerlo, así que se busca
     * la mayor en JPEG o PNG.
     */
    private String mejorMiniatura(JsonNode video) {
        JsonNode miniaturas = video.get("thumbnails");
        String mejorUrl = null;
        int mejorAncho = Integer.MIN_VALUE;
        if (miniaturas != null && miniaturas.isArray()) {
            for (JsonNode m : miniaturas) {
                String url = texto(m, "url");
                if (url == null || !formatoAdmitido(url)) {
                    continue;
                }
                int ancho = primerNoNulo(entero(m, "width"), primerNoNulo(entero(m, "preference"), 0));
                if (ancho > mejorAncho) {
                    mejorAncho = ancho;
                    mejorUrl = url;
                }
            }
        }
        if (mejorUrl != null) {
            return mejorUrl;
        }
        String directa = texto(video, "thumbnail");
        return directa != null && formatoAdmitido(directa) ? directa : null;
    }

    private boolean formatoAdmitido(String url) {
        String sinParametros = url.toLowerCase(Locale.ROOT).split("\\?", 2)[0];
        return sinParametros.endsWith(".jpg") || sinParametros.endsWith(".jpeg")
                || sinParametros.endsWith(".png");
    }

    private List<SubtitleTrack> leerSubtitulos(JsonNode nodo, boolean automaticos) {
        if (nodo == null || !nodo.isObject()) {
            return List.of();
        }
        List<SubtitleTrack> pistas = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> campos = nodo.properties().iterator();
        while (campos.hasNext()) {
            String codigo = campos.next().getKey();
            if (codigo == null || codigo.isBlank() || codigo.startsWith("live_chat")) {
                continue;
            }
            String nombre = Idiomas.nombre(codigo);
            pistas.add(new SubtitleTrack(codigo, nombre != null ? nombre : codigo, automaticos));
        }
        pistas.sort(Comparator.comparing(SubtitleTrack::nombre));
        return pistas;
    }

    /** yt-dlp da la fecha como 20141110; se convierte a una fecha de verdad. */
    private static java.time.LocalDate fechaSubida(JsonNode video) {
        String fecha = texto(video, "upload_date");
        if (fecha == null || fecha.length() != 8) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(fecha, java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ lectura segura

    private static String texto(JsonNode nodo, String campo) {
        if (nodo == null) {
            return null;
        }
        JsonNode valor = nodo.get(campo);
        if (valor == null || valor.isNull() || !valor.isValueNode()) {
            return null;
        }
        String texto = valor.asText();
        return texto == null || texto.isBlank() || "NA".equals(texto) ? null : texto;
    }

    private static Integer entero(JsonNode nodo, String campo) {
        if (nodo == null) {
            return null;
        }
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() || !valor.isNumber() ? null : valor.asInt();
    }

    private static Long largo(JsonNode nodo, String campo) {
        if (nodo == null) {
            return null;
        }
        JsonNode valor = nodo.get(campo);
        if (valor == null || valor.isNull() || !valor.isNumber()) {
            return null;
        }
        long bytes = valor.asLong();
        return bytes > 0 ? bytes : null;
    }

    private static double decimal(JsonNode nodo, String campo, double porDefecto) {
        if (nodo == null) {
            return porDefecto;
        }
        JsonNode valor = nodo.get(campo);
        return valor == null || valor.isNull() || !valor.isNumber() ? porDefecto : valor.asDouble();
    }

    private static boolean booleano(JsonNode nodo, String campo) {
        if (nodo == null) {
            return false;
        }
        JsonNode valor = nodo.get(campo);
        return valor != null && valor.isBoolean() && valor.asBoolean();
    }

    @SafeVarargs
    private static <T> T primerNoNulo(T... valores) {
        for (T v : valores) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
