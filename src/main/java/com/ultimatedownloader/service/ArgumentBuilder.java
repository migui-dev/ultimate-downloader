package com.ultimatedownloader.service;

import com.ultimatedownloader.model.AudioOutputFormat;
import com.ultimatedownloader.model.CookieBrowser;
import com.ultimatedownloader.model.DownloadMode;
import com.ultimatedownloader.model.DownloadRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye las listas de argumentos para yt-dlp. Nunca se concatenan cadenas: cada argumento
 * es un elemento de la lista, así que las rutas con espacios o acentos llegan intactas.
 */
public final class ArgumentBuilder {

    /** Argumentos para recodificar a algo que cualquier editor (Premiere incluido) abre bien. */
    private static final String ARGUMENTOS_RECODIFICACION =
            "VideoConvertor:-c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p "
            + "-c:a aac -b:a 192k -movflags +faststart";

    /** Contenedores en los que yt-dlp sabe incrustar la miniatura (WebM no está). */
    private static final java.util.Set<String> CONTENEDORES_CON_MINIATURA = java.util.Set.of(
            "mp3", "mkv", "mka", "ogg", "opus", "flac", "m4a", "mp4", "m4v", "mov");

    private final Path ytDlp;
    private final Path ffmpeg;

    public ArgumentBuilder(Path ytDlp, Path ffmpeg) {
        this.ytDlp = ytDlp;
        this.ffmpeg = ffmpeg;
    }

    /** yt-dlp -J: metadatos y lista de formatos en JSON. */
    public List<String> analisis(String url, CookieBrowser navegador, String rutaPerfilCookies) {
        return analisis(url, navegador, rutaPerfilCookies, null);
    }

    /** Igual, pero pidiéndole el vídeo a otro cliente de YouTube. */
    public List<String> analisis(String url, CookieBrowser navegador, String rutaPerfilCookies,
                                 String cliente) {
        List<String> args = base();
        anadirCliente(args, cliente);
        args.add("-J");
        args.add("--no-warnings");
        args.add("--no-playlist");
        args.add("--playlist-items");
        args.add("1");
        anadirCookies(args, navegador, rutaPerfilCookies);
        args.add("--");
        args.add(url);
        return args;
    }

    /**
     * Clientes de YouTube a los que recurrir cuando el de siempre falla, en orden.
     *
     * web_embedded devuelve el catálogo entero, así que no se pierde calidad; android solo da
     * el formato 18, de 360p, pero es la red de seguridad cuando no queda otra.
     */
    public static final List<String> CLIENTES_ALTERNATIVOS = List.of("web_embedded", "android");

    /** --extractor-args para pedirle el vídeo a otro cliente de YouTube. */
    static void anadirCliente(List<String> args, String cliente) {
        if (cliente == null || cliente.isBlank()) {
            return;
        }
        args.add("--extractor-args");
        args.add("youtube:player_client=" + cliente);
    }

    /** yt-dlp --version. */
    public List<String> version() {
        List<String> args = new ArrayList<>();
        args.add(ytDlp.toString());
        args.add("--ignore-config");
        args.add("--version");
        return args;
    }

    /**
     * Orden que comprueba si las cookies configuradas se pueden leer de verdad.
     *
     * Hace falta un vídeo porque yt-dlp no tiene un modo «solo cargar las cookies»; con
     * --simulate no descarga nada, solo pide la página y cuenta lo que ha leído.
     */
    public List<String> comprobacionCookies(CookieBrowser navegador, String rutaPerfil,
                                            String url) {
        List<String> args = new ArrayList<>();
        args.add(ytDlp.toString());
        args.add("--ignore-config");
        args.add("--simulate");
        args.add("--no-warnings");
        args.add("--verbose");
        anadirCookies(args, navegador, rutaPerfil);
        args.add(url);
        return args;
    }

    /** yt-dlp -U: actualiza el propio ejecutable. */
    public List<String> actualizacion() {
        List<String> args = new ArrayList<>();
        args.add(ytDlp.toString());
        args.add("--ignore-config");
        args.add("-U");
        return args;
    }

    /** Orden completa de descarga para una petición concreta. */
    public List<String> descarga(DownloadRequest p) {
        List<String> args = base();
        anadirCliente(args, p.clienteAlternativo());

        // Progreso en un formato fijo y fácil de parsear, línea a línea.
        args.add("--progress");
        args.add("--newline");
        args.add("--progress-template");
        args.add(ProgressParser.PLANTILLA_DESCARGA);
        args.add("--progress-template");
        args.add(ProgressParser.PLANTILLA_POSPROCESO);
        args.add("--print");
        args.add(ProgressParser.PLANTILLA_RUTA_FINAL);
        // --print activa el modo silencioso, y entonces yt-dlp no cuenta lo que va haciendo.
        // Con --no-quiet se recupera esa salida, que es la que se ve en la consola.
        args.add("--no-quiet");

        args.add(p.listaCompleta() ? "--yes-playlist" : "--no-playlist");
        args.add("--retries");
        args.add("10");
        args.add("--fragment-retries");
        args.add("10");
        args.add("--concurrent-fragments");
        args.add(String.valueOf(p.fragmentosSimultaneos()));
        args.add("--no-mtime");
        args.add("--windows-filenames");
        args.add("--trim-filenames");
        args.add("200");

        if (ffmpeg != null) {
            args.add("--ffmpeg-location");
            args.add(ffmpeg.toString());
        }

        args.add("-P");
        args.add(p.carpetaDestino().toString());
        args.add("-o");
        args.add(plantillaSalida(p));

        args.add("-f");
        args.add(selectorFormato(p));

        if (p.modo() == DownloadMode.SOLO_AUDIO) {
            anadirSalidaAudio(args, p.formatoAudio());
        } else {
            anadirSalidaVideo(args, p);
        }

        anadirSubtitulos(args, p);

        // ffmpeg solo sabe meter una portada en algunos contenedores; en WebM la descarga
        // fallaría entera, así que se omite la opción en lugar de romper la descarga.
        if (p.incrustarMiniatura() && admiteMiniatura(p.extensionEsperada())) {
            args.add("--embed-thumbnail");
        }
        if (p.incrustarMetadatos()) {
            args.add("--embed-metadata");
        }

        anadirCookies(args, p.navegadorCookies(), p.rutaPerfilCookies());

        args.add("--");
        args.add(p.url());
        return args;
    }

    // ------------------------------------------------------------------ piezas

    private List<String> base() {
        List<String> args = new ArrayList<>();
        args.add(ytDlp.toString());
        // --ignore-config evita que un fichero de configuración del usuario cambie el
        // comportamiento que la aplicación da por supuesto.
        args.add("--ignore-config");
        args.add("--no-colors");
        args.add("--encoding");
        args.add("UTF-8");
        return args;
    }

    /**
     * Selector de formato (-f). Si no se elige una pista concreta se usan los genéricos de yt-dlp,
     * con alternativa a un formato único por si el vídeo no ofreciera pistas separadas.
     */
    String selectorFormato(DownloadRequest p) {
        String video = p.idFormatoVideo();
        String audio = p.idFormatoAudio();
        return switch (p.modo()) {
            case SOLO_VIDEO -> video != null ? video : "bestvideo/best";
            case SOLO_AUDIO -> audio != null ? audio : "bestaudio/best";
            case VIDEO_Y_AUDIO -> {
                if (video == null && audio == null) {
                    yield "bestvideo*+bestaudio/best";
                }
                String v = video != null ? video : "bestvideo";
                String a = audio != null ? audio : "bestaudio";
                yield v + "+" + a;
            }
        };
    }

    /** Plantilla de -o; el nombre ya viene saneado y sin colisiones. */
    String plantillaSalida(DownloadRequest p) {
        if (p.nombreBase() != null && !p.nombreBase().isBlank()) {
            return p.nombreBase() + ".%(ext)s";
        }
        return p.listaCompleta()
                ? "%(playlist_index)03d - %(title)s.%(ext)s"
                : "%(title)s.%(ext)s";
    }

    private void anadirSalidaVideo(List<String> args, DownloadRequest p) {
        if (p.recodificar()) {
            args.add("--recode-video");
            args.add("mp4");
            args.add("--postprocessor-args");
            args.add(ARGUMENTOS_RECODIFICACION);
            return;
        }
        // Remuxear es un cambio de contenedor sin recomprimir: las pistas se copian tal cual.
        String ext = p.contenedor().extension();
        args.add("--merge-output-format");
        args.add(ext);
        args.add("--remux-video");
        args.add(ext);
    }

    private void anadirSalidaAudio(List<String> args, AudioOutputFormat formato) {
        if (!formato.requiereConversion()) {
            return;
        }
        args.add("-x");
        args.add("--audio-format");
        args.add(formato.codigoYtDlp());
        if (formato.calidad() != null) {
            args.add("--audio-quality");
            args.add(formato.calidad());
        }
    }

    private void anadirSubtitulos(List<String> args, DownloadRequest p) {
        if (!p.descargarSubtitulos() || p.idiomasSubtitulos().isEmpty()) {
            return;
        }
        args.add("--write-subs");
        if (p.subtitulosAutomaticos()) {
            args.add("--write-auto-subs");
        }
        args.add("--sub-langs");
        args.add(String.join(",", p.idiomasSubtitulos()));
        args.add("--convert-subs");
        args.add("srt");
        if (p.incrustarSubtitulos() && p.modo() != DownloadMode.SOLO_AUDIO) {
            args.add("--embed-subs");
        }
    }

    /** true si ese contenedor admite una portada incrustada. */
    static boolean admiteMiniatura(String extension) {
        return extension != null
                && CONTENEDORES_CON_MINIATURA.contains(extension.toLowerCase(java.util.Locale.ROOT));
    }

    private void anadirCookies(List<String> args, CookieBrowser navegador, String rutaPerfil) {
        if (navegador == null || !navegador.activo()) {
            return;
        }
        if (navegador.usaArchivo()) {
            if (rutaPerfil != null && !rutaPerfil.isBlank()) {
                args.add("--cookies");
                args.add(rutaPerfil);
            }
            return;
        }
        String valor = navegador.codigoYtDlp();
        if (rutaPerfil != null && !rutaPerfil.isBlank()) {
            // Sintaxis de yt-dlp: NAVEGADOR:PERFIL. Opera GX la necesita porque su perfil
            // no está donde yt-dlp busca el de Opera.
            valor = valor + ":" + rutaPerfil;
        }
        args.add("--cookies-from-browser");
        args.add(valor);
    }
}
