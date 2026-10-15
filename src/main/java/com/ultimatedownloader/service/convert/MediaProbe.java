package com.ultimatedownloader.service.convert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.convert.MediaInfo;
import com.ultimatedownloader.model.convert.PistaMedia;
import com.ultimatedownloader.model.convert.TipoPista;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.ErrorTranslator;
import com.ultimatedownloader.service.ProcessRunner;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lee con ffprobe lo que hay dentro de un archivo. Se pide el JSON completo de una vez y se
 * normaliza, igual que con el de yt-dlp: los campos que faltan y las pistas que no interesan
 * (las miniaturas incrustadas van como adjuntos) se filtran aquí.
 */
public final class MediaProbe {

    private static final Logger log = LoggerFactory.getLogger(MediaProbe.class);

    /** Analizar un archivo local es inmediato; si tarda más, algo va mal. */
    private static final Duration LIMITE = Duration.ofSeconds(60);

    private final Path ffprobe;
    private final ObjectMapper mapper = new ObjectMapper();

    public MediaProbe(Path ffprobe) {
        this.ffprobe = ffprobe;
    }

    /** Argumentos de ffprobe para un archivo. */
    public List<String> argumentos(Path archivo) {
        return List.of(
                ffprobe.toString(),
                "-v", "quiet",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                archivo.toString());
    }

    /** Analiza un archivo. Es bloqueante: hay que llamarlo desde un hilo de trabajo. */
    public MediaInfo analizar(Path archivo) throws AppException {
        try {
            ProcessRunner.Resultado resultado = ProcessRunner.ejecutar(argumentos(archivo), LIMITE);
            if (!resultado.correcto() || resultado.salida().isBlank()) {
                throw new AppException(new ErrorAmistoso(
                        "No se ha podido leer «" + archivo.getFileName() + "».",
                        "Comprueba que es un archivo de audio o de vídeo que ffmpeg entienda.",
                        resultado.error()));
            }
            return analizar(archivo, resultado.salida());
        } catch (IOException e) {
            throw new AppException(ErrorTranslator.traducir(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(new ErrorAmistoso(Textos.t("convertir.analisis_cancelado"), null), e);
        }
    }

    /** Analiza un JSON ya leído; es lo que usan los tests. */
    public MediaInfo analizar(Path archivo, String json) throws AppException {
        try {
            return analizar(archivo, mapper.readTree(json));
        } catch (JsonProcessingException e) {
            throw new AppException(new ErrorAmistoso(
                    "La respuesta de ffprobe no se ha entendido.",
                    "Puede que el archivo esté dañado.", e.getOriginalMessage()), e);
        }
    }

    private MediaInfo analizar(Path archivo, JsonNode raiz) {
        JsonNode formato = raiz.get("format");
        List<PistaMedia> pistas = new ArrayList<>();
        Map<TipoPista, Integer> contadores = new EnumMap<>(TipoPista.class);

        JsonNode flujos = raiz.get("streams");
        if (flujos != null && flujos.isArray()) {
            for (JsonNode flujo : flujos) {
                TipoPista tipo = TipoPista.desdeFfprobe(texto(flujo, "codec_type"));
                if (tipo == TipoPista.OTRO) {
                    // Adjuntos y datos sueltos: las miniaturas incrustadas entran por aquí.
                    continue;
                }
                int indiceEnTipo = contadores.merge(tipo, 0, (viejo, nuevo) -> viejo + 1);
                pistas.add(leerPista(flujo, tipo, indiceEnTipo));
            }
        }

        return new MediaInfo(
                archivo,
                texto(formato, "format_name"),
                Duration.ofMillis(Math.round(decimal(formato, "duration", 0) * 1000)),
                largo(formato, "size"),
                largo(formato, "bit_rate"),
                pistas);
    }

    private PistaMedia leerPista(JsonNode flujo, TipoPista tipo, int indiceEnTipo) {
        JsonNode etiquetas = flujo.get("tags");
        return new PistaMedia(
                entero(flujo, "index", 0),
                indiceEnTipo,
                tipo,
                texto(flujo, "codec_name"),
                texto(flujo, "profile"),
                entero(flujo, "width", 0),
                entero(flujo, "height", 0),
                fraccion(texto(flujo, "avg_frame_rate"), texto(flujo, "r_frame_rate")),
                texto(flujo, "pix_fmt"),
                entero(flujo, "bits_per_raw_sample", 0),
                entero(flujo, "sample_rate", 0),
                entero(flujo, "channels", 0),
                texto(flujo, "channel_layout"),
                texto(flujo, "sample_fmt"),
                largo(flujo, "bit_rate"),
                texto(etiquetas, "language"),
                texto(etiquetas, "title"));
    }

    /**
     * ffprobe da los fotogramas como una fracción. Se usa la media, que es la real del archivo;
     * r_frame_rate es la máxima teórica y en tasa variable engaña.
     */
    static double fraccion(String media, String maxima) {
        double valor = aFraccion(media);
        return valor > 0 ? valor : aFraccion(maxima);
    }

    private static double aFraccion(String texto) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        String[] partes = texto.split("/");
        try {
            if (partes.length == 2) {
                double divisor = Double.parseDouble(partes[1]);
                return divisor == 0 ? 0 : Double.parseDouble(partes[0]) / divisor;
            }
            return Double.parseDouble(texto);
        } catch (NumberFormatException e) {
            log.debug("Fracción de fotogramas no reconocida: {}", texto);
            return 0;
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
        return texto == null || texto.isBlank() ? null : texto;
    }

    private static int entero(JsonNode nodo, String campo, int porDefecto) {
        String texto = texto(nodo, campo);
        if (texto == null) {
            return porDefecto;
        }
        try {
            return Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    private static long largo(JsonNode nodo, String campo) {
        String texto = texto(nodo, campo);
        if (texto == null) {
            return 0;
        }
        try {
            return Long.parseLong(texto);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double decimal(JsonNode nodo, String campo, double porDefecto) {
        String texto = texto(nodo, campo);
        if (texto == null) {
            return porDefecto;
        }
        try {
            return Double.parseDouble(texto);
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
