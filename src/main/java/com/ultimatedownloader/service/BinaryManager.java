package com.ultimatedownloader.service;

import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.util.Textos;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Localiza, y si hace falta descarga, yt-dlp y ffmpeg. Se buscan por este orden: la ruta
 * indicada a mano, la carpeta de datos, la de instalación y el PATH.
 *
 * Lo que se descarga va a la carpeta de datos, que siempre es escribible: en Program Files o
 * en /usr/bin «yt-dlp -U» no podría actualizarse.
 *
 * De ffmpeg hay compilación oficial para Windows y para Linux. En macOS no, así que ahí se
 * busca el del sistema (Homebrew, MacPorts) y, si falta, se explica cómo instalarlo.
 */
public final class BinaryManager {

    private static final Logger log = LoggerFactory.getLogger(BinaryManager.class);

    private static final String BASE_YT_DLP =
            "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";

    /** Compilaciones de ffmpeg que mantiene el propio proyecto yt-dlp. */
    private static final String BASE_FFMPEG =
            "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/";

    private static final String NOMBRE_YT_DLP = Plataforma.ejecutable("yt-dlp");
    private static final String NOMBRE_FFMPEG = Plataforma.ejecutable("ffmpeg");
    private static final String NOMBRE_FFPROBE = Plataforma.ejecutable("ffprobe");

    /** Último yt-dlp publicado para este sistema. */
    public static final String URL_YT_DLP = urlYtDlp(Plataforma.actual());

    /** Paquete de ffmpeg para este sistema, o null si no hay uno oficial. */
    public static final String URL_FFMPEG = urlFfmpeg(Plataforma.actual());

    static String urlYtDlp(Plataforma sistema) {
        return BASE_YT_DLP + switch (sistema) {
            case WINDOWS -> "yt-dlp.exe";
            case MAC -> "yt-dlp_macos";
            case LINUX -> "yt-dlp_linux";
        };
    }

    /** macOS no tiene compilación oficial en este repositorio: ahí se usa la del sistema. */
    static String urlFfmpeg(Plataforma sistema) {
        return switch (sistema) {
            case WINDOWS -> BASE_FFMPEG + "ffmpeg-master-latest-win64-gpl.zip";
            case LINUX -> BASE_FFMPEG + "ffmpeg-master-latest-linux64-gpl.tar.xz";
            case MAC -> null;
        };
    }

    /** true si se puede bajar ffmpeg solo; en macOS lo instala el usuario. */
    public static boolean hayDescargaDeFfmpeg() {
        return URL_FFMPEG != null;
    }

    private final Path carpetaHerramientas;
    private final List<Path> carpetasExtra;

    public BinaryManager() {
        this(AppPaths.carpetaHerramientas(), carpetasDondeMirarAdemas());
    }

    /**
     * Sitios donde buscar aparte de la carpeta de datos, por orden. Los que vienen dentro del
     * paquete van los primeros de esta lista, pero después de la carpeta de datos: así, si
     * alguna vez se descarga una versión más nueva, es esa la que manda.
     */
    private static List<Path> carpetasDondeMirarAdemas() {
        List<Path> carpetas = new ArrayList<>();
        Path incluidas = AppPaths.carpetaHerramientasIncluidas();
        if (incluidas != null) {
            carpetas.add(incluidas);
        }
        carpetas.add(AppPaths.carpetaAplicacion());
        carpetas.add(AppPaths.carpetaAplicacion().resolve("herramientas"));
        carpetas.add(AppPaths.carpetaAplicacion().resolve("tools"));
        return carpetas;
    }

    /** Constructor para los tests: permite indicar dónde buscar. */
    public BinaryManager(Path carpetaHerramientas, List<Path> carpetasExtra) {
        this.carpetaHerramientas = carpetaHerramientas;
        this.carpetasExtra = List.copyOf(carpetasExtra);
    }

    public Path carpetaHerramientas() {
        return carpetaHerramientas;
    }

    /** Busca yt-dlp; rutaManual tiene prioridad sobre todo lo demás. */
    public Optional<Path> localizarYtDlp(String rutaManual) {
        return localizar(NOMBRE_YT_DLP, rutaManual);
    }

    /** Busca ffmpeg; rutaManual tiene prioridad sobre todo lo demás. */
    public Optional<Path> localizarFfmpeg(String rutaManual) {
        return localizar(NOMBRE_FFMPEG, rutaManual);
    }

    private Optional<Path> localizar(String nombre, String rutaManual) {
        Path manual = aRuta(rutaManual);
        if (manual != null && Files.isRegularFile(manual)) {
            return Optional.of(manual);
        }
        List<Path> candidatas = new ArrayList<>();
        candidatas.add(carpetaHerramientas.resolve(nombre));
        for (Path carpeta : carpetasExtra) {
            candidatas.add(carpeta.resolve(nombre));
        }
        candidatas.addAll(enRutaDelSistema(nombre));

        for (Path candidata : candidatas) {
            if (Files.isRegularFile(candidata)) {
                log.debug("{} encontrado en {}", nombre, candidata);
                return Optional.of(candidata.toAbsolutePath());
            }
        }
        log.info("No se ha encontrado {}", nombre);
        return Optional.empty();
    }

    private List<Path> enRutaDelSistema(String nombre) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return List.of();
        }
        List<Path> resultado = new ArrayList<>();
        for (String trozo : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path carpeta = aRuta(trozo);
            if (carpeta != null) {
                resultado.add(carpeta.resolve(nombre));
            }
        }
        return resultado;
    }

    // ------------------------------------------------------------------ descargas

    /** Descarga yt-dlp. El progreso va de 0 a 1, o negativo si no se conoce. */
    public Path descargarYtDlp(DoubleConsumer progreso) throws AppException {
        Path destino = carpetaHerramientas.resolve(NOMBRE_YT_DLP);
        descargar(URL_YT_DLP, destino, progreso);
        marcarEjecutable(destino);
        log.info("yt-dlp descargado en {}", destino);
        return destino;
    }

    /**
     * Fuera de Windows lo descargado llega sin permiso de ejecución y no arranca.
     */
    private static void marcarEjecutable(Path archivo) {
        if (Plataforma.esWindows()) {
            return;
        }
        if (!archivo.toFile().setExecutable(true, false)) {
            log.warn("No se ha podido marcar {} como ejecutable", archivo);
        }
    }

    /** Descarga el paquete de ffmpeg y saca de él ffmpeg y ffprobe. */
    public Path descargarFfmpeg(DoubleConsumer progreso) throws AppException {
        if (!hayDescargaDeFfmpeg()) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("binarios.sin_ffmpeg_para_este_sistema"),
                    Textos.t("binarios.instala_ffmpeg_a_mano")));
        }
        String extension = Plataforma.esWindows() ? ".zip" : ".tar.xz";
        Path paquete = AppPaths.carpetaTemporal().resolve("ffmpeg" + extension);
        try {
            descargar(URL_FFMPEG, paquete, progreso);
            Path ffmpeg = Plataforma.esWindows()
                    ? extraerDelZip(paquete)
                    : extraerDelTar(paquete);
            marcarEjecutable(ffmpeg);
            marcarEjecutable(ffmpeg.resolveSibling(NOMBRE_FFPROBE));
            log.info("ffmpeg descargado en {}", ffmpeg);
            return ffmpeg;
        } finally {
            try {
                Files.deleteIfExists(paquete);
            } catch (IOException e) {
                log.debug("No se ha podido borrar {}: {}", paquete, e.getMessage());
            }
        }
    }

    /**
     * Saca ffmpeg y ffprobe de un .tar.xz con el «tar» del sistema, que en Linux y macOS viene
     * de serie y entiende xz. Java no trae descompresor de xz y no merece la pena añadir una
     * dependencia entera para esto.
     */
    private Path extraerDelTar(Path paquete) throws AppException {
        try {
            Files.createDirectories(carpetaHerramientas);
            ProcessRunner.Resultado resultado = ProcessRunner.ejecutar(List.of(
                    "tar", "-xJf", paquete.toString(),
                    "-C", carpetaHerramientas.toString(),
                    "--strip-components=2",
                    "--wildcards", "*/bin/ffmpeg", "*/bin/ffprobe"), Duration.ofMinutes(5));
            if (resultado.codigoSalida() != 0) {
                throw new AppException(new ErrorAmistoso(
                        Textos.t("binarios.no_se_ha_podido_descomprimir"),
                        Textos.t("binarios.vuelve_a_intentarlo"),
                        resultado.error()));
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AppException(new ErrorAmistoso(
                    Textos.t("binarios.no_se_ha_podido_descomprimir"),
                    Textos.t("binarios.vuelve_a_intentarlo"),
                    e.toString()), e);
        }
        Path ffmpeg = carpetaHerramientas.resolve(NOMBRE_FFMPEG);
        if (!Files.isRegularFile(ffmpeg)) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("binarios.zip_sin_ejecutable"),
                    Textos.t("binarios.vuelve_a_intentarlo"), null));
        }
        return ffmpeg;
    }

    private Path extraerDelZip(Path zip) throws AppException {
        Path ffmpeg = null;
        try (ZipInputStream entrada = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry elemento;
            while ((elemento = entrada.getNextEntry()) != null) {
                String nombre = elemento.getName().toLowerCase(Locale.ROOT);
                String destino = null;
                if (nombre.endsWith("bin/" + NOMBRE_FFMPEG)) {
                    destino = NOMBRE_FFMPEG;
                } else if (nombre.endsWith("bin/" + NOMBRE_FFPROBE)) {
                    destino = NOMBRE_FFPROBE;
                }
                if (destino == null) {
                    continue;
                }
                // Solo se escriben nombres fijos nuestros, nunca la ruta que venga en el ZIP.
                Path salida = carpetaHerramientas.resolve(destino);
                Files.createDirectories(carpetaHerramientas);
                Files.copy(entrada, salida, StandardCopyOption.REPLACE_EXISTING);
                if (destino.equals(NOMBRE_FFMPEG)) {
                    ffmpeg = salida;
                }
            }
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("binarios.no_se_ha_podido_descomprimir"),
                    Textos.t("binarios.vuelve_a_intentarlo"),
                    e.toString()), e);
        }
        if (ffmpeg == null) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("binarios.zip_sin_ejecutable"),
                    Textos.t("binarios.vuelve_a_intentarlo"),
                    null));
        }
        return ffmpeg;
    }

    /** Descarga un archivo informando del avance; escribe primero a .parcial. */
    private void descargar(String url, Path destino, DoubleConsumer progreso) throws AppException {
        Path parcial = destino.resolveSibling(destino.getFileName() + ".parcial");
        try {
            Files.createDirectories(destino.getParent());
            HttpClient cliente = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "UltimateDownloader")
                    .GET()
                    .build();

            HttpResponse<InputStream> respuesta =
                    cliente.send(peticion, HttpResponse.BodyHandlers.ofInputStream());
            if (respuesta.statusCode() != 200) {
                throw new IOException("El servidor ha respondido " + respuesta.statusCode());
            }
            long total = respuesta.headers().firstValueAsLong("content-length").orElse(-1);

            try (InputStream entrada = respuesta.body();
                 OutputStream salida = Files.newOutputStream(parcial)) {
                byte[] bufer = new byte[1 << 16];
                long copiados = 0;
                int ultimoPorciento = -1;
                int leidos;
                while ((leidos = entrada.read(bufer)) > 0) {
                    salida.write(bufer, 0, leidos);
                    copiados += leidos;
                    if (progreso == null) {
                        continue;
                    }
                    if (total <= 0) {
                        // Sin content-length no hay porcentaje que dar: barra indeterminada.
                        if (ultimoPorciento < 0) {
                            ultimoPorciento = 0;
                            progreso.accept(-1);
                        }
                        continue;
                    }
                    // Un aviso por punto porcentual: uno por bloque de 64 kB son miles de
                    // saltos al hilo de la interfaz para mover la barra un pelo.
                    int porciento = (int) (100 * copiados / total);
                    if (porciento > ultimoPorciento) {
                        ultimoPorciento = porciento;
                        progreso.accept((double) copiados / total);
                    }
                }
            }
            Files.move(parcial, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            borrarSilencioso(parcial);
            throw new AppException(ErrorTranslator.traducir(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            borrarSilencioso(parcial);
            throw new AppException(new ErrorAmistoso(Textos.t("modelos.descarga_cancelada"), null), e);
        }
    }

    private void borrarSilencioso(Path ruta) {
        try {
            Files.deleteIfExists(ruta);
        } catch (IOException e) {
            log.debug("No se ha podido borrar {}: {}", ruta, e.getMessage());
        }
    }

    private static Path aRuta(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Paths.get(valor.trim());
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
