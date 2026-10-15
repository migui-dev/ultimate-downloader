package com.ultimatedownloader.viewmodel;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.fx.EnSegundoPlano;
import com.ultimatedownloader.model.AudioCodec;
import com.ultimatedownloader.model.AudioFormat;
import com.ultimatedownloader.model.AudioOutputFormat;
import com.ultimatedownloader.model.CompatibilidadContenedor;
import com.ultimatedownloader.model.CookieBrowser;
import com.ultimatedownloader.model.DownloadMode;
import com.ultimatedownloader.model.DownloadRequest;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.EstimacionTamano;
import com.ultimatedownloader.model.Settings;
import com.ultimatedownloader.model.SubtitleTrack;
import com.ultimatedownloader.model.VideoCodec;
import com.ultimatedownloader.model.VideoContainer;
import com.ultimatedownloader.model.VideoFormat;
import com.ultimatedownloader.model.VideoInfo;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.ContainerCompatibility;
import com.ultimatedownloader.service.FilenameTemplate;
import com.ultimatedownloader.service.FolderValidator;
import com.ultimatedownloader.service.SizeEstimator;
import com.ultimatedownloader.service.consola.NivelConsola;
import com.ultimatedownloader.util.Textos;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableObjectValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Estado de la pantalla principal: lo que el usuario elige y lo que se deduce de ello (tamaño
 * estimado, compatibilidad, nombre del archivo). La vista solo se enlaza a estas propiedades.
 */
public final class MainViewModel {

    private static final Logger log = LoggerFactory.getLogger(MainViewModel.class);

    private final Contexto contexto;

    // ------------------------------------------------------------------ entrada

    private final StringProperty url = new SimpleStringProperty("");
    private final ObjectProperty<VideoInfo> info = new SimpleObjectProperty<>();
    private final ObjectProperty<DownloadMode> modo = new SimpleObjectProperty<>(DownloadMode.VIDEO_Y_AUDIO);

    /**
     * Pista elegida en la tabla. Puede ser la fila centinela «Mejor disponible», que es la que
     * viene marcada; en ese caso se delega en los selectores de yt-dlp.
     */
    private final ObjectProperty<VideoFormat> videoElegido = new SimpleObjectProperty<>();
    private final ObjectProperty<AudioFormat> audioElegido = new SimpleObjectProperty<>();

    private final ObjectProperty<VideoContainer> contenedor = new SimpleObjectProperty<>(VideoContainer.MKV);
    private final ObjectProperty<AudioOutputFormat> formatoAudio = new SimpleObjectProperty<>(AudioOutputFormat.ORIGINAL);
    private final BooleanProperty recodificar = new SimpleBooleanProperty(false);

    private final StringProperty plantillaNombre = new SimpleStringProperty(FilenameTemplate.PLANTILLA_POR_DEFECTO);

    /** La carpeta la lleva el bloque de destino de la vista; aquí solo se consulta. */
    private Supplier<Path> destino = () -> null;

    private final BooleanProperty descargarSubtitulos = new SimpleBooleanProperty(false);
    private final BooleanProperty subtitulosAutomaticos = new SimpleBooleanProperty(false);
    private final BooleanProperty incrustarSubtitulos = new SimpleBooleanProperty(true);
    private final BooleanProperty incrustarMiniatura = new SimpleBooleanProperty(true);
    private final BooleanProperty incrustarMetadatos = new SimpleBooleanProperty(true);
    private final BooleanProperty listaCompleta = new SimpleBooleanProperty(false);
    private final ObservableList<SubtitleTrack> subtitulosDisponibles = FXCollections.observableArrayList();
    private final ObservableList<String> idiomasSubtitulos = FXCollections.observableArrayList();
    private final ObjectProperty<CookieBrowser> navegadorCookies = new SimpleObjectProperty<>(CookieBrowser.NINGUNO);

    // ------------------------------------------------------------------ salida

    private final ObservableList<VideoFormat> formatosVideo = FXCollections.observableArrayList();
    private final ObservableList<AudioFormat> formatosAudio = FXCollections.observableArrayList();

    private final BooleanProperty analizando = new SimpleBooleanProperty(false);
    private final ObjectProperty<ErrorAmistoso> error = new SimpleObjectProperty<>();
    private final StringProperty aviso = new SimpleStringProperty();

    private final ObservableObjectValue<VideoFormat> videoEfectivo;
    private final ObservableObjectValue<AudioFormat> audioEfectivo;
    private final ObservableObjectValue<EstimacionTamano> estimacion;
    private final ObservableObjectValue<CompatibilidadContenedor> compatibilidad;
    private final ObservableObjectValue<String> nombrePrevisto;

    public MainViewModel(Contexto contexto) {
        this.contexto = contexto;
        aplicarAjustes(contexto.ajustes());

        videoEfectivo = Bindings.createObjectBinding(
                () -> usaMejorVideo()
                        ? info.get() == null ? null : info.get().mejorVideo().orElse(null)
                        : videoElegido.get(),
                videoElegido, info);

        audioEfectivo = Bindings.createObjectBinding(
                () -> usaMejorAudio()
                        ? info.get() == null ? null : info.get().mejorAudio().orElse(null)
                        : audioElegido.get(),
                audioElegido, info);

        estimacion = Bindings.createObjectBinding(
                () -> SizeEstimator.estimar(videoEfectivo.get(), audioEfectivo.get(),
                        info.get() != null ? info.get().duracion() : null,
                        modo.get(), formatoAudio.get(), recodificar.get()),
                videoEfectivo, audioEfectivo, info, modo, formatoAudio, recodificar);

        compatibilidad = Bindings.createObjectBinding(
                () -> ContainerCompatibility.comprobar(contenedor.get(),
                        videoEfectivo.get() != null ? videoEfectivo.get().codec() : VideoCodec.NINGUNO,
                        codecDeAudioEfectivo(),
                        modo.get()),
                videoEfectivo, audioEfectivo, contenedor, modo, recodificar);

        nombrePrevisto = Bindings.createObjectBinding(
                () -> info.get() == null ? "" : FilenameTemplate.aplicar(plantillaNombre.get(),
                        info.get(), videoEfectivo.get(), audioEfectivo.get()),
                info, plantillaNombre, videoEfectivo, audioEfectivo);

        guardarEnAjustesAlCambiar();
    }

    /** true si no hay pista de vídeo elegida a mano. */
    public boolean usaMejorVideo() {
        VideoFormat elegido = videoElegido.get();
        return elegido == null || elegido.esMejorDisponible();
    }

    /** true si no hay pista de audio elegida a mano. */
    public boolean usaMejorAudio() {
        AudioFormat elegido = audioElegido.get();
        return elegido == null || elegido.esMejorDisponible();
    }

    /** Carga en el modelo los valores guardados entre sesiones. */
    public void aplicarAjustes(Settings s) {
        modo.set(s.getModoPorDefecto());
        contenedor.set(s.getContenedorPorDefecto());
        formatoAudio.set(s.getFormatoAudioPorDefecto());
        plantillaNombre.set(s.getPlantillaNombre());
        incrustarMiniatura.set(s.isIncrustarMiniatura());
        incrustarMetadatos.set(s.isIncrustarMetadatos());
        descargarSubtitulos.set(s.isDescargarSubtitulos());
        subtitulosAutomaticos.set(s.isSubtitulosAutomaticos());
        incrustarSubtitulos.set(s.isIncrustarSubtitulos());
        navegadorCookies.set(s.getNavegadorCookies());
        idiomasSubtitulos.setAll(s.getIdiomasSubtitulos());
    }

    /**
     * Refleja en los ajustes cada cambio. Solo toca el objeto en memoria: el fichero se escribe al
     * cerrar o al cambiar la carpeta, para no escribir en disco con cada tecla.
     */
    private void guardarEnAjustesAlCambiar() {
        Settings s = contexto.ajustes();
        modo.addListener((obs, antes, ahora) -> s.setModoPorDefecto(ahora));
        contenedor.addListener((obs, antes, ahora) -> s.setContenedorPorDefecto(ahora));
        formatoAudio.addListener((obs, antes, ahora) -> s.setFormatoAudioPorDefecto(ahora));
        plantillaNombre.addListener((obs, antes, ahora) -> s.setPlantillaNombre(ahora));
        incrustarMiniatura.addListener((obs, antes, ahora) -> s.setIncrustarMiniatura(ahora));
        incrustarMetadatos.addListener((obs, antes, ahora) -> s.setIncrustarMetadatos(ahora));
        descargarSubtitulos.addListener((obs, antes, ahora) -> s.setDescargarSubtitulos(ahora));
        subtitulosAutomaticos.addListener((obs, antes, ahora) -> s.setSubtitulosAutomaticos(ahora));
        incrustarSubtitulos.addListener((obs, antes, ahora) -> s.setIncrustarSubtitulos(ahora));
        navegadorCookies.addListener((obs, antes, ahora) -> s.setNavegadorCookies(ahora));
        idiomasSubtitulos.addListener((ListChangeListener<String>) cambio ->
                s.setIdiomasSubtitulos(new java.util.ArrayList<>(idiomasSubtitulos)));
    }

    // ------------------------------------------------------------------ acciones

    /** Analiza el enlace en segundo plano. alTerminar se llama en el hilo de la interfaz. */
    public void analizar(Runnable alTerminar) {
        String enlace = url.get() != null ? url.get().strip() : "";
        if (enlace.isEmpty()) {
            error.set(new ErrorAmistoso(Textos.t("descargar.pega_el_enlace"), null));
            return;
        }
        if (!contexto.rutaYtDlp().isPresent()) {
            error.set(new ErrorAmistoso(Textos.t("descargar.no_se_encuentra_ytdlp"),
                    Textos.t("descargar.abrelo_desde_ajustes")));
            return;
        }

        error.set(null);
        aviso.set(null);
        analizando.set(true);
        contexto.consola().registrar("app", Textos.t("descargar.consola_analizando", enlace));

        EnSegundoPlano.calcular(contexto.tareas(),
                () -> contexto.ytDlp().analizar(enlace, navegadorCookies.get(),
                        contexto.ajustes().getRutaPerfilCookies()),
                resultado -> {
                    analizando.set(false);
                    contexto.consola().registrar("app", Textos.t("descargar.consola_analizado",
                            resultado.titulo(), resultado.formatosVideo().size(),
                            resultado.formatosAudio().size()));
                    establecerInfo(resultado);
                    alTerminar.run();
                },
                causa -> {
                    analizando.set(false);
                    log.warn("No se ha podido analizar «{}»", enlace, causa);
                    ErrorAmistoso amistoso = causa instanceof AppException app
                            ? app.error()
                            : new ErrorAmistoso(Textos.t("descargar.no_se_ha_podido_analizar"),
                                    Textos.t("comun.consulta_la_consola"), String.valueOf(causa));
                    contexto.consola().registrar("app", NivelConsola.ERROR, amistoso.mensaje());
                    if (amistoso.detalle() != null) {
                        contexto.consola().registrar("yt-dlp", NivelConsola.ERROR,
                                amistoso.detalle());
                    }
                    error.set(amistoso);
                    alTerminar.run();
                });
    }

    private void establecerInfo(VideoInfo nueva) {
        info.set(nueva);

        // La primera fila de cada tabla es «Mejor disponible», y es la que queda marcada.
        formatosVideo.setAll(VideoFormat.mejorDisponible());
        formatosVideo.addAll(nueva.formatosVideo());
        formatosAudio.setAll(AudioFormat.mejorDisponible());
        formatosAudio.addAll(nueva.formatosAudio());
        videoElegido.set(formatosVideo.get(0));
        audioElegido.set(formatosAudio.get(0));

        List<SubtitleTrack> pistas = new java.util.ArrayList<>(nueva.subtitulos());
        pistas.addAll(nueva.subtitulosAutomaticos());
        subtitulosDisponibles.setAll(pistas);

        if (nueva.enDirecto()) {
            aviso.set(Textos.t("descargar.emision_en_directo"));
        } else if (nueva.perteneceALista()) {
            aviso.set(Textos.t("descargar.pertenece_a_una_lista", nueva.videosEnLista()));
        }
        ajustarContenedorSiHaceFalta();
    }

    /**
     * Si el contenedor elegido no admite los códecs del vídeo, propone el que sí. No cambia nada a
     * espaldas del usuario: deja el aviso preparado para que la vista lo muestre.
     */
    private void ajustarContenedorSiHaceFalta() {
        if (!contexto.ajustes().isAvisarIncompatibilidad()) {
            return;
        }
        CompatibilidadContenedor c = compatibilidad.get();
        if (c != null && !c.compatible()) {
            log.debug("Contenedor incompatible: {}", c.aviso());
        }
    }

    /** Cambia a MKV, que acepta cualquier códec sin recomprimir. */
    public void usarContenedorCompatible() {
        VideoContainer alternativa = ContainerCompatibility.contenedorSinRecomprimir(
                videoEfectivo.get() != null ? videoEfectivo.get().codec() : VideoCodec.NINGUNO,
                codecDeAudioEfectivo());
        if (alternativa != null) {
            recodificar.set(false);
            contenedor.set(alternativa);
        }
    }

    /**
     * Construye la petición de descarga con el estado actual.
     *
     * @return la petición, o vacío si falta algo (y entonces errorProperty() lo explica)
     */
    public Optional<DownloadRequest> construirPeticion() {
        VideoInfo v = info.get();
        if (v == null) {
            error.set(new ErrorAmistoso(Textos.t("descargar.analiza_primero"), null));
            return Optional.empty();
        }
        Path destino = this.destino.get();
        FolderValidator.Resultado carpetaOk =
                FolderValidator.validar(destino, estimacion.get().bytes());
        if (!carpetaOk.valida()) {
            error.set(new ErrorAmistoso(carpetaOk.mensaje(),
                    Textos.t("comun.elige_otra_carpeta_destino")));
            return Optional.empty();
        }

        boolean lista = listaCompleta.get();
        String nombre = lista ? null : FilenameTemplate.asegurarUnico(destino,
                FilenameTemplate.aplicar(plantillaNombre.get(), v, videoEfectivo.get(), audioEfectivo.get()),
                extensionesPosibles());

        DownloadRequest peticion = DownloadRequest.builder()
                .url(v.urlPagina() != null && !v.urlPagina().isBlank() ? v.urlPagina() : url.get().strip())
                .modo(modo.get())
                .idFormatoVideo(usaMejorVideo() || !modo.get().incluyeVideo()
                        ? null : idDe(videoElegido.get()))
                .idFormatoAudio(usaMejorAudio() || !modo.get().incluyeAudio() || audioYaIncluido()
                        ? null : idDe(audioElegido.get()))
                .contenedor(contenedor.get())
                .formatoAudio(formatoAudio.get())
                .recodificar(recodificar.get())
                .carpetaDestino(destino)
                .nombreBase(nombre)
                .subtitulos(descargarSubtitulos.get(), List.copyOf(idiomasSubtitulos),
                        subtitulosAutomaticos.get(), incrustarSubtitulos.get())
                .incrustarMiniatura(incrustarMiniatura.get())
                .incrustarMetadatos(incrustarMetadatos.get())
                .listaCompleta(lista)
                .cookies(navegadorCookies.get(), contexto.ajustes().getRutaPerfilCookies())
                .tituloPrevisto(v.titulo())
                .estimacion(estimacion.get())
                .fragmentosSimultaneos(contexto.ajustes().getFragmentosSimultaneos())
                .extensionOrigenAudio(audioEfectivo.get() != null
                        ? audioEfectivo.get().contenedor() : null)
                .build();

        error.set(null);
        return Optional.of(peticion);
    }

    /** Extensiones que puede acabar teniendo el archivo, para no pisar uno existente. */
    private String[] extensionesPosibles() {
        if (modo.get() == DownloadMode.SOLO_AUDIO) {
            AudioOutputFormat f = formatoAudio.get();
            if (f.extension() != null) {
                return new String[]{f.extension()};
            }
            AudioFormat origen = audioEfectivo.get();
            return origen != null && origen.contenedor() != null
                    ? new String[]{origen.contenedor()}
                    : new String[]{"m4a", "webm", "opus"};
        }
        return recodificar.get()
                ? new String[]{"mp4"}
                : new String[]{contenedor.get().extension()};
    }

    private boolean audioYaIncluido() {
        VideoFormat v = videoEfectivo.get();
        return modo.get() == DownloadMode.VIDEO_Y_AUDIO && v != null && v.incluyeAudio();
    }

    private AudioCodec codecDeAudioEfectivo() {
        VideoFormat v = videoEfectivo.get();
        if (v != null && v.incluyeAudio()) {
            return v.audioIncluido();
        }
        AudioFormat a = audioEfectivo.get();
        return a != null ? a.codec() : AudioCodec.NINGUNO;
    }

    private static String idDe(VideoFormat f) {
        return f != null ? f.formatId() : null;
    }

    private static String idDe(AudioFormat f) {
        return f != null ? f.formatId() : null;
    }

    // ------------------------------------------------------------------ propiedades

    public StringProperty urlProperty() {
        return url;
    }

    public ObjectProperty<VideoInfo> infoProperty() {
        return info;
    }

    public ObjectProperty<DownloadMode> modoProperty() {
        return modo;
    }

    public ObjectProperty<VideoFormat> videoElegidoProperty() {
        return videoElegido;
    }

    public ObjectProperty<AudioFormat> audioElegidoProperty() {
        return audioElegido;
    }

    public ObjectProperty<VideoContainer> contenedorProperty() {
        return contenedor;
    }

    public ObjectProperty<AudioOutputFormat> formatoAudioProperty() {
        return formatoAudio;
    }

    public BooleanProperty recodificarProperty() {
        return recodificar;
    }

    public StringProperty plantillaNombreProperty() {
        return plantillaNombre;
    }

    /** De dónde sale la carpeta de destino al construir la petición. */
    public void destino(Supplier<Path> carpeta) {
        this.destino = carpeta != null ? carpeta : () -> null;
    }

    public BooleanProperty descargarSubtitulosProperty() {
        return descargarSubtitulos;
    }

    public BooleanProperty subtitulosAutomaticosProperty() {
        return subtitulosAutomaticos;
    }

    public BooleanProperty incrustarSubtitulosProperty() {
        return incrustarSubtitulos;
    }

    public BooleanProperty incrustarMiniaturaProperty() {
        return incrustarMiniatura;
    }

    public BooleanProperty incrustarMetadatosProperty() {
        return incrustarMetadatos;
    }

    public BooleanProperty listaCompletaProperty() {
        return listaCompleta;
    }

    public ObservableList<SubtitleTrack> subtitulosDisponibles() {
        return subtitulosDisponibles;
    }

    public ObservableList<String> idiomasSubtitulos() {
        return idiomasSubtitulos;
    }

    public ObjectProperty<CookieBrowser> navegadorCookiesProperty() {
        return navegadorCookies;
    }

    public ObservableList<VideoFormat> formatosVideo() {
        return formatosVideo;
    }

    public ObservableList<AudioFormat> formatosAudio() {
        return formatosAudio;
    }

    public BooleanProperty analizandoProperty() {
        return analizando;
    }

    public ObjectProperty<ErrorAmistoso> errorProperty() {
        return error;
    }

    public StringProperty avisoProperty() {
        return aviso;
    }

    public ObservableObjectValue<VideoFormat> videoEfectivo() {
        return videoEfectivo;
    }

    public ObservableObjectValue<AudioFormat> audioEfectivo() {
        return audioEfectivo;
    }

    public ObservableObjectValue<EstimacionTamano> estimacion() {
        return estimacion;
    }

    public ObservableObjectValue<CompatibilidadContenedor> compatibilidad() {
        return compatibilidad;
    }

    public ObservableObjectValue<String> nombrePrevisto() {
        return nombrePrevisto;
    }

    public Contexto contexto() {
        return contexto;
    }
}
