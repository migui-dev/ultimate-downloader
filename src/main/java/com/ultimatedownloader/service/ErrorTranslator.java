package com.ultimatedownloader.service;

import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.util.Textos;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Locale;

/**
 * Traduce la salida de error de yt-dlp y ffmpeg a mensajes claros. El usuario nunca ve una
 * traza ni un mensaje en inglés; el texto original se guarda en el detalle, para el registro.
 */
public final class ErrorTranslator {

    /**
     * Si el texto contiene alguna de las marcas, se usa este mensaje. Se guarda la clave y no el
     * texto porque el mensaje depende del idioma y las reglas se construyen una sola vez.
     */
    private record Regla(List<String> marcas, String clave) {

        ErrorAmistoso mensaje(String detalle) {
            return new ErrorAmistoso(Textos.t("error." + clave + ".mensaje"),
                    Textos.t("error." + clave + ".sugerencia"), detalle);
        }
    }

    private static final List<Regla> REGLAS = List.of(
            new Regla(List.of("is not a valid url", "unsupported url", "unable to extract video id",
                            "not a valid url"),
                    "enlace_no_valido"),

            // Lo que dice yt-dlp de verdad cuando no puede leer las cookies. Sin estas
            // reglas el usuario veía «activa las cookies» teniéndolas ya activadas.
            new Regla(List.of("failed to decrypt with dpapi", "app-bound encryption",
                            "could not decrypt", "none of the cookies"),
                    "cookies_cifradas"),

            new Regla(List.of("could not copy chrome cookie database",
                            "could not copy cookie database", "database is locked"),
                    "cookies_navegador_abierto"),

            new Regla(List.of("cookies database in", "could not find cookies file",
                            "no such file or directory: cookies"),
                    "cookies_sin_perfil"),

            new Regla(List.of("private video", "this video is private"),
                    "privado"),

            new Regla(List.of("members-only", "join this channel", "available to this channel"),
                    "solo_miembros"),

            new Regla(List.of("sign in to confirm your age", "inappropriate for some users",
                            "age-restricted", "age restricted", "confirm your age"),
                    "restriccion_edad"),

            new Regla(List.of("confirm you're not a bot", "confirm you are not a bot",
                            "sign in to confirm"),
                    "no_eres_robot"),

            new Regla(List.of("available in your country", "blocked it in your country",
                            "blocked it on copyright grounds", "not available from your location",
                            "geo restricted", "geo-restricted"),
                    "no_disponible_pais"),

            new Regla(List.of("video unavailable", "has been removed", "no longer available",
                            "this video is unavailable", "removed by the uploader",
                            "account associated with this video has been terminated"),
                    "no_disponible"),

            new Regla(List.of("this live event will begin", "premieres in", "is not yet available"),
                    "emision_no_empezada"),

            new Regla(List.of("has drm", "drm protected", "_has_drm"),
                    "drm"),

            new Regla(List.of("getaddrinfo failed", "temporary failure in name resolution",
                            "no address associated", "failed to resolve", "11001",
                            "network is unreachable", "connection aborted", "connection reset",
                            "timed out", "10054", "10060", "unable to download webpage"),
                    "sin_conexion"),

            new Regla(List.of("no space left on device", "not enough space on the disk",
                            "winerror 112", "disk full"),
                    "sin_espacio"),

            new Regla(List.of("permission denied", "access is denied", "winerror 5",
                            "unable to open for writing", "errno 13"),
                    "sin_permisos"),

            new Regla(List.of("ffmpeg not found", "ffmpeg is not installed",
                            "ffmpeg or avconv not found", "you have requested merging"),
                    "falta_ffmpeg"),

            new Regla(List.of("ffmpeg exited with code", "postprocessing:", "conversion failed",
                            "ffprobe", "invalid argument", "could not write header"),
                    "ffmpeg_fallo"),

            new Regla(List.of("http error 429", "too many requests"),
                    "demasiadas_peticiones"),

            new Regla(List.of("requested format is not available", "requested format not available"),
                    "formato_no_disponible"),

            new Regla(List.of("nsig extraction failed", "signature extraction failed",
                            "unable to extract yt initial data", "unable to extract player",
                            "please report this issue", "unable to extract"),
                    "ytdlp_desactualizado"),

            new Regla(List.of("http error 403", "forbidden"),
                    "error_403"),

            new Regla(List.of("unable to rename file", "used by another process", "winerror 32"),
                    "archivo_en_uso"));

    private ErrorTranslator() {
    }

    /** Traduce la salida de error de un proceso que ha terminado mal. */
    public static ErrorAmistoso traducir(int codigoSalida, String salidaError) {
        String detalle = salidaError != null ? salidaError.strip() : "";
        String texto = detalle.toLowerCase(Locale.ROOT);

        for (Regla regla : REGLAS) {
            for (String marca : regla.marcas()) {
                if (texto.contains(marca)) {
                    return regla.mensaje(detalle);
                }
            }
        }

        if (detalle.isEmpty()) {
            return new ErrorAmistoso(Textos.t("error.fallo_con_codigo", codigoSalida),
                    Textos.t("error.consulta_el_registro"), detalle);
        }
        return new ErrorAmistoso(Textos.t("error.fallo"),
                Textos.t("error.consulta_el_registro"), detalle);
    }

    /** Traduce una excepción de Java que no venga de un proceso externo. */
    public static ErrorAmistoso traducir(Throwable causa) {
        String detalle = causa != null ? causa.toString() : "";
        if (causa instanceof java.io.FileNotFoundException
                || causa instanceof NoSuchFileException) {
            return new ErrorAmistoso(Textos.t("error.no_se_encuentra_archivo"),
                    Textos.t("error.comprueba_la_ruta"), detalle);
        }
        if (causa instanceof AccessDeniedException) {
            return new ErrorAmistoso(Textos.t("error.sin_permisos.mensaje"),
                    Textos.t("comun.elige_otra_carpeta"), detalle);
        }
        if (causa instanceof java.net.UnknownHostException
                || causa instanceof java.net.ConnectException
                || causa instanceof java.net.SocketTimeoutException) {
            return new ErrorAmistoso(Textos.t("error.sin_internet"),
                    Textos.t("error.revisa_la_conexion"), detalle);
        }
        if (causa instanceof InterruptedException) {
            return new ErrorAmistoso(Textos.t("error.cancelada"), null, detalle);
        }
        return traducir(-1, detalle);
    }
}
