package com.ultimatedownloader.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Todo lo que yt-dlp necesita saber para una descarga, inmutable.
 *
 * Cada petición lleva su propia carpeta de destino, para que cambiarla en la pantalla no
 * afecte a lo que ya está en la cola.
 */
public record DownloadRequest(
        String url,
        DownloadMode modo,
        String idFormatoVideo,
        String idFormatoAudio,
        VideoContainer contenedor,
        AudioOutputFormat formatoAudio,
        boolean recodificar,
        Path carpetaDestino,
        String nombreBase,
        boolean descargarSubtitulos,
        List<String> idiomasSubtitulos,
        boolean subtitulosAutomaticos,
        boolean incrustarSubtitulos,
        boolean incrustarMiniatura,
        boolean incrustarMetadatos,
        boolean listaCompleta,
        CookieBrowser navegadorCookies,
        String rutaPerfilCookies,
        String tituloPrevisto,
        EstimacionTamano estimacion,
        int fragmentosSimultaneos,
        String extensionOrigenAudio,
        String clienteAlternativo) {

    public DownloadRequest {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(modo, "modo");
        Objects.requireNonNull(carpetaDestino, "carpetaDestino");
        contenedor = contenedor != null ? contenedor : VideoContainer.MKV;
        formatoAudio = formatoAudio != null ? formatoAudio : AudioOutputFormat.ORIGINAL;
        navegadorCookies = navegadorCookies != null ? navegadorCookies : CookieBrowser.NINGUNO;
        estimacion = estimacion != null ? estimacion : EstimacionTamano.DESCONOCIDA;
        idiomasSubtitulos = idiomasSubtitulos != null ? List.copyOf(idiomasSubtitulos) : List.of();
        fragmentosSimultaneos = Math.max(1, fragmentosSimultaneos);
    }

    /**
     * Pistas que va a bajar yt-dlp, para repartir la barra cuando no hay tamaño estimado. En
     * vídeo + audio son dos descargas seguidas; en los demás modos, una.
     */
    public int pistasPrevistas() {
        return modo == DownloadMode.VIDEO_Y_AUDIO ? 2 : 1;
    }

    /**
     * Extensión esperada del archivo final, para comprobar colisiones de nombre y si cabe la
     * miniatura. Con el audio original es la que entregue YouTube: webm para Opus, m4a para AAC.
     */
    public String extensionEsperada() {
        if (modo == DownloadMode.SOLO_AUDIO) {
            if (formatoAudio.extension() != null) {
                return formatoAudio.extension();
            }
            return extensionOrigenAudio != null && !extensionOrigenAudio.isBlank()
                    ? extensionOrigenAudio
                    : "m4a";
        }
        return recodificar ? "mp4" : contenedor.extension();
    }

    public Builder copia() {
        return new Builder()
                .url(url)
                .modo(modo)
                .idFormatoVideo(idFormatoVideo)
                .idFormatoAudio(idFormatoAudio)
                .contenedor(contenedor)
                .formatoAudio(formatoAudio)
                .recodificar(recodificar)
                .carpetaDestino(carpetaDestino)
                .nombreBase(nombreBase)
                .subtitulos(descargarSubtitulos, idiomasSubtitulos, subtitulosAutomaticos, incrustarSubtitulos)
                .incrustarMiniatura(incrustarMiniatura)
                .incrustarMetadatos(incrustarMetadatos)
                .listaCompleta(listaCompleta)
                .cookies(navegadorCookies, rutaPerfilCookies)
                .tituloPrevisto(tituloPrevisto)
                .estimacion(estimacion)
                .fragmentosSimultaneos(fragmentosSimultaneos)
                .extensionOrigenAudio(extensionOrigenAudio)
                .clienteAlternativo(clienteAlternativo);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Constructor fluido: la petición tiene demasiados campos para un constructor normal. */
    public static final class Builder {

        private String url;
        private DownloadMode modo = DownloadMode.VIDEO_Y_AUDIO;
        private String idFormatoVideo;
        private String idFormatoAudio;
        private VideoContainer contenedor = VideoContainer.MKV;
        private AudioOutputFormat formatoAudio = AudioOutputFormat.ORIGINAL;
        private boolean recodificar;
        private Path carpetaDestino;
        private String nombreBase;
        private boolean descargarSubtitulos;
        private List<String> idiomasSubtitulos = List.of();
        private boolean subtitulosAutomaticos;
        private boolean incrustarSubtitulos;
        private boolean incrustarMiniatura;
        private boolean incrustarMetadatos;
        private boolean listaCompleta;
        private CookieBrowser navegadorCookies = CookieBrowser.NINGUNO;
        private String rutaPerfilCookies;
        private String tituloPrevisto;
        private EstimacionTamano estimacion = EstimacionTamano.DESCONOCIDA;
        private int fragmentosSimultaneos = 4;
        private String extensionOrigenAudio;
        private String clienteAlternativo;

        public Builder url(String valor) {
            this.url = valor;
            return this;
        }

        public Builder modo(DownloadMode valor) {
            this.modo = valor;
            return this;
        }

        public Builder idFormatoVideo(String valor) {
            this.idFormatoVideo = valor;
            return this;
        }

        public Builder idFormatoAudio(String valor) {
            this.idFormatoAudio = valor;
            return this;
        }

        public Builder contenedor(VideoContainer valor) {
            this.contenedor = valor;
            return this;
        }

        public Builder formatoAudio(AudioOutputFormat valor) {
            this.formatoAudio = valor;
            return this;
        }

        public Builder recodificar(boolean valor) {
            this.recodificar = valor;
            return this;
        }

        public Builder carpetaDestino(Path valor) {
            this.carpetaDestino = valor;
            return this;
        }

        public Builder nombreBase(String valor) {
            this.nombreBase = valor;
            return this;
        }

        public Builder subtitulos(boolean descargar, List<String> idiomas,
                                  boolean automaticos, boolean incrustar) {
            this.descargarSubtitulos = descargar;
            this.idiomasSubtitulos = idiomas != null ? List.copyOf(idiomas) : List.of();
            this.subtitulosAutomaticos = automaticos;
            this.incrustarSubtitulos = incrustar;
            return this;
        }

        public Builder incrustarMiniatura(boolean valor) {
            this.incrustarMiniatura = valor;
            return this;
        }

        public Builder incrustarMetadatos(boolean valor) {
            this.incrustarMetadatos = valor;
            return this;
        }

        public Builder listaCompleta(boolean valor) {
            this.listaCompleta = valor;
            return this;
        }

        public Builder cookies(CookieBrowser navegador, String rutaPerfil) {
            this.navegadorCookies = navegador;
            this.rutaPerfilCookies = rutaPerfil;
            return this;
        }

        public Builder tituloPrevisto(String valor) {
            this.tituloPrevisto = valor;
            return this;
        }

        public Builder estimacion(EstimacionTamano valor) {
            this.estimacion = valor;
            return this;
        }

        public Builder fragmentosSimultaneos(int valor) {
            this.fragmentosSimultaneos = valor;
            return this;
        }

        /** Extensión de la pista de audio de origen, para el modo «audio original». */
        /** Cliente de YouTube con el que reintentar cuando el de siempre falla. */
        public Builder clienteAlternativo(String valor) {
            this.clienteAlternativo = valor;
            return this;
        }

        public Builder extensionOrigenAudio(String valor) {
            this.extensionOrigenAudio = valor;
            return this;
        }

        public DownloadRequest build() {
            return new DownloadRequest(url, modo, idFormatoVideo, idFormatoAudio, contenedor,
                    formatoAudio, recodificar, carpetaDestino, nombreBase, descargarSubtitulos,
                    idiomasSubtitulos, subtitulosAutomaticos, incrustarSubtitulos,
                    incrustarMiniatura, incrustarMetadatos, listaCompleta, navegadorCookies,
                    rutaPerfilCookies, tituloPrevisto, estimacion, fragmentosSimultaneos,
                    extensionOrigenAudio,
                    clienteAlternativo);
        }
    }
}
