package com.ultimatedownloader.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ultimatedownloader.model.CookieBrowser;
import com.ultimatedownloader.model.DownloadRequest;
import com.ultimatedownloader.model.CookieBrowser;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.VideoInfo;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fachada sobre yt-dlp: analizar, descargar, consultar la versión y actualizar. Todo bloquea,
 * así que se llama desde un hilo de trabajo y nunca desde el de JavaFX.
 */
public final class YtDlpService {

    private static final Logger log = LoggerFactory.getLogger(YtDlpService.class);

    /**
     * Vídeo con el que se prueban las cookies: el primero que se subió a YouTube, de 19
     * segundos y público desde 2005. Vale cualquiera; lo que importa es que siga ahí.
     */
    private static final String VIDEO_DE_PRUEBA = "https://www.youtube.com/watch?v=jNQXAC9IVRw";

    private static final Pattern COOKIES_EXTRAIDAS =
            Pattern.compile("Extracted (\\d+) cookies");

    /** Analizar un enlace rara vez pasa de unos segundos; si tarda más, algo va mal. */
    private static final Duration LIMITE_ANALISIS = Duration.ofSeconds(120);
    private static final Duration LIMITE_VERSION = Duration.ofSeconds(30);
    private static final Duration LIMITE_ACTUALIZACION = Duration.ofMinutes(5);

    private final ArgumentBuilder argumentos;
    private final FormatJsonParser parser = new FormatJsonParser();

    public YtDlpService(Path ytDlp, Path ffmpeg) {
        this.argumentos = new ArgumentBuilder(ytDlp, ffmpeg);
    }

    /** Analiza un enlace y devuelve los metadatos y los formatos disponibles. */
    public VideoInfo analizar(String url, CookieBrowser navegador, String rutaPerfilCookies)
            throws AppException {
        if (!esEnlaceValido(url)) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("ytdlp.enlace_no_valido"),
                    Textos.t("ytdlp.pega_la_direccion")));
        }
        ProcessRunner.Resultado resultado = ejecutar(
                argumentos.analisis(url.strip(), navegador, rutaPerfilCookies), LIMITE_ANALISIS);

        if (!resultado.correcto() || resultado.salida().isBlank()) {
            AppException fallo = new AppException(
                    ErrorTranslator.traducir(resultado.codigoSalida(), resultado.error()));
            if (!esFalloDeCliente(resultado.error())) {
                throw fallo;
            }
            return reintentarConOtroCliente(url.strip(), navegador, rutaPerfilCookies, fallo);
        }
        try {
            VideoInfo info = parser.analizar(resultado.salida());
            log.info("Analizado «{}»: {} formatos de vídeo y {} de audio", info.titulo(),
                    info.formatosVideo().size(), info.formatosAudio().size());
            if (info.tieneDrm()) {
                throw new AppException(new ErrorAmistoso(
                        Textos.t("error.drm.mensaje"),
                        Textos.t("error.drm.sugerencia")));
            }
            if (info.formatosVideo().isEmpty() && info.formatosAudio().isEmpty()) {
                throw new AppException(new ErrorAmistoso(
                        Textos.t("ytdlp.sin_formatos"),
                        Textos.t("ytdlp.puede_ser_directo")));
            }
            return info;
        } catch (JsonProcessingException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("ytdlp.respuesta_no_entendida"),
                    Textos.t("error.ytdlp_desactualizado.sugerencia"),
                    e.getOriginalMessage()), e);
        } catch (IllegalArgumentException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("ytdlp.sin_videos"),
                    Textos.t("ytdlp.comprueba_la_lista"),
                    e.getMessage()), e);
        }
    }

    /** Versión instalada de yt-dlp. */
    public String version() throws AppException {
        ProcessRunner.Resultado resultado = ejecutar(argumentos.version(), LIMITE_VERSION);
        if (!resultado.correcto()) {
            throw new AppException(
                    ErrorTranslator.traducir(resultado.codigoSalida(), resultado.error()));
        }
        return resultado.salida().strip();
    }

    /**
     * Si el fallo es de los que se arreglan preguntándole a otro cliente de YouTube.
     *
     * Son los que no dependen del vídeo en sí: el aviso de robot, un reproductor que YouTube
     * ha cambiado o una respuesta sin formatos. Un vídeo privado o borrado va a fallar igual
     * con todos, y ahí no se gana nada reintentando.
     */
    public static boolean esFalloDeCliente(String salidaDeError) {
        if (salidaDeError == null || salidaDeError.isBlank()) {
            return false;
        }
        String texto = salidaDeError.toLowerCase(Locale.ROOT);
        return texto.contains("confirm you're not a bot")
                || texto.contains("confirm you are not a bot")
                || texto.contains("sign in to confirm")
                || texto.contains("no video formats found")
                || texto.contains("the page needs to be reloaded")
                || texto.contains("failed to extract any player response")
                || texto.contains("player response")
                || texto.contains("nsig extraction failed")
                || texto.contains("unable to extract");
    }

    /**
     * Prueba el análisis con los clientes alternativos de YouTube.
     *
     * YouTube rechaza de vez en cuando al cliente de siempre —el aviso de «confirma que no
     * eres un robot», o un vídeo que de pronto no tiene formatos— y con otro contesta sin
     * problema. Antes eso era un error y ahí se quedaba el usuario.
     */
    private VideoInfo reintentarConOtroCliente(String url, CookieBrowser navegador,
                                               String rutaPerfilCookies, AppException original)
            throws AppException {
        for (String cliente : ArgumentBuilder.CLIENTES_ALTERNATIVOS) {
            log.info("Reintentando el análisis con el cliente {}", cliente);
            try {
                ProcessRunner.Resultado resultado = ejecutar(
                        argumentos.analisis(url, navegador, rutaPerfilCookies, cliente),
                        LIMITE_ANALISIS);
                if (resultado.correcto()) {
                    log.info("El cliente {} sí ha respondido", cliente);
                    return parser.analizar(resultado.salida());
                }
            } catch (AppException | JsonProcessingException e) {
                log.debug("El cliente {} tampoco: {}", cliente, e.getMessage());
            }
        }
        throw original;
    }

    /**
     * Comprueba si las cookies configuradas se pueden leer de verdad.
     *
     * Existe porque hasta ahora el usuario no se enteraba de que no se podían hasta que
     * fallaba una descarga, y el mensaje que le llegaba era que activara las cookies, que era
     * justo lo que ya había hecho.
     */
    public ComprobacionCookies comprobarCookies(CookieBrowser navegador, String ruta) {
        if (navegador == null || !navegador.activo()) {
            return ComprobacionCookies.bien(Textos.t("settings.cookies_desactivadas"));
        }
        if (navegador.usaArchivo()) {
            ComprobacionCookies delArchivo = revisarArchivo(ruta);
            if (!delArchivo.correcto()) {
                return delArchivo;
            }
        }
        try {
            ProcessRunner.Resultado resultado = ejecutar(
                    argumentos.comprobacionCookies(navegador, ruta, VIDEO_DE_PRUEBA),
                    LIMITE_ANALISIS);
            if (resultado.correcto()) {
                return ComprobacionCookies.bien(Textos.t("settings.cookies_funcionan",
                        resumenDeCookies(resultado.error(), navegador)));
            }
            return ComprobacionCookies.mal(ErrorTranslator
                    .traducir(resultado.codigoSalida(), resultado.error()).mensaje());
        } catch (AppException e) {
            return ComprobacionCookies.mal(e.error().mensaje());
        }
    }

    /** «Extracted 123 cookies from chrome» es lo que escribe yt-dlp cuando lo consigue. */
    private static String resumenDeCookies(String salida, CookieBrowser navegador) {
        Matcher cuenta = COOKIES_EXTRAIDAS.matcher(salida != null ? salida : "");
        if (cuenta.find()) {
            return Textos.t("settings.cuantas_cookies", Integer.parseInt(cuenta.group(1)));
        }
        return navegador.nombreLegible();
    }

    /**
     * Mira el cookies.txt antes de gastar una petición: que exista, que tenga el formato de
     * Netscape y que lleve dentro una sesión de YouTube.
     */
    private static ComprobacionCookies revisarArchivo(String ruta) {
        if (ruta == null || ruta.isBlank()) {
            return ComprobacionCookies.mal(Textos.t("settings.falta_el_archivo_de_cookies"));
        }
        Path archivo = Path.of(ruta);
        if (!Files.isRegularFile(archivo)) {
            return ComprobacionCookies.mal(Textos.t("settings.no_existe_el_archivo_de_cookies"));
        }
        try {
            String contenido = Files.readString(archivo);
            if (!contenido.contains("youtube.com")) {
                return ComprobacionCookies.mal(Textos.t("settings.cookies_sin_youtube"));
            }
            if (!contenido.contains("LOGIN_INFO") && !contenido.contains("SAPISID")) {
                return ComprobacionCookies.mal(Textos.t("settings.cookies_sin_sesion"));
            }
        } catch (IOException e) {
            return ComprobacionCookies.mal(Textos.t("settings.no_se_ha_podido_leer_cookies"));
        }
        return ComprobacionCookies.bien("");
    }

    /**
     * Ejecuta yt-dlp -U.
     *
     * @return el texto que ha devuelto yt-dlp, para mostrárselo al usuario
     */
    public String actualizar() throws AppException {
        ProcessRunner.Resultado resultado = ejecutar(argumentos.actualizacion(), LIMITE_ACTUALIZACION);
        String texto = (resultado.salida() + "\n" + resultado.error()).strip();
        if (!resultado.correcto()) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("settings.no_se_ha_podido_actualizar"),
                    Textos.t("ytdlp.actualiza_desde_ahi"),
                    texto));
        }
        log.info("Resultado de la actualización: {}", texto);
        return texto;
    }

    /** Lanza una descarga. Las dos salidas se entregan por separado, línea a línea. */
    public ProcessRunner.ProcesoEnCurso descargar(DownloadRequest peticion,
                                                  Consumer<String> porLinea,
                                                  Consumer<String> porLineaError)
            throws AppException {
        List<String> orden = argumentos.descarga(peticion);
        try {
            return ProcessRunner.lanzar(orden, porLinea, porLineaError);
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("ytdlp.no_se_ha_podido_ejecutar"),
                    Textos.t("ytdlp.comprueba_la_ruta"),
                    e.toString()), e);
        }
    }

    private ProcessRunner.Resultado ejecutar(List<String> orden, Duration limite)
            throws AppException {
        try {
            return ProcessRunner.ejecutar(orden, limite);
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("ytdlp.no_se_ha_podido_ejecutar"),
                    Textos.t("ytdlp.comprueba_la_ruta"),
                    e.toString()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(new ErrorAmistoso(Textos.t("error.cancelada"), null), e);
        }
    }

    /** Comprobación mínima: tiene que ser una dirección http o https. */
    static boolean esEnlaceValido(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String u = url.strip().toLowerCase(Locale.ROOT);
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return false;
        }
        try {
            return java.net.URI.create(url.strip()).getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
