package com.ultimatedownloader.viewmodel;

import com.ultimatedownloader.Contexto;
import com.ultimatedownloader.fx.EnSegundoPlano;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.calidad.Calidades;
import com.ultimatedownloader.model.calidad.FormatoSalida;
import com.ultimatedownloader.model.calidad.NivelCalidad;
import com.ultimatedownloader.model.calidad.PerfilCalidad;
import com.ultimatedownloader.model.calidad.ValidadorCalidad;
import com.ultimatedownloader.model.convert.ConversionRequest;
import com.ultimatedownloader.model.convert.MediaInfo;
import com.ultimatedownloader.model.convert.PistaMedia;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.FilenameTemplate;
import com.ultimatedownloader.service.consola.NivelConsola;
import com.ultimatedownloader.service.convert.RemuxDecider;
import com.ultimatedownloader.util.Textos;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Estado del módulo Convertir: la lista de archivos, lo que ffprobe sabe de cada uno y las
 * opciones de salida. El análisis va en segundo plano, que abrir un vídeo grande se nota.
 */
public final class ConvertirViewModel {

    private static final Logger log = LoggerFactory.getLogger(ConvertirViewModel.class);

    private final Contexto contexto;

    private final ObservableList<MediaInfo> archivos = FXCollections.observableArrayList();
    private final ObjectProperty<MediaInfo> seleccionado = new SimpleObjectProperty<>();

    private final ObjectProperty<FormatoSalida> formato = new SimpleObjectProperty<>(FormatoSalida.MP4);
    private final ObjectProperty<NivelCalidad> nivel = new SimpleObjectProperty<>(NivelCalidad.MEDIA);
    private final BooleanProperty remuxear = new SimpleBooleanProperty(true);
    private final BooleanProperty soloAudio = new SimpleBooleanProperty(false);
    private final BooleanProperty usarGpu = new SimpleBooleanProperty(false);

    private final ObjectProperty<PistaMedia> pistaAudio = new SimpleObjectProperty<>();
    private final StringProperty ancho = new SimpleStringProperty("");
    private final StringProperty alto = new SimpleStringProperty("");
    private final StringProperty fps = new SimpleStringProperty("");
    private final StringProperty frecuencia = new SimpleStringProperty("");
    private final StringProperty canales = new SimpleStringProperty("");
    private final StringProperty inicio = new SimpleStringProperty("");
    private final StringProperty fin = new SimpleStringProperty("");

    /** La carpeta la lleva el bloque de destino de la vista; aquí solo se consulta. */
    private Supplier<Path> destino = () -> null;
    private final BooleanProperty analizando = new SimpleBooleanProperty(false);
    private final ObjectProperty<ErrorAmistoso> error = new SimpleObjectProperty<>();

    public ConvertirViewModel(Contexto contexto) {
        this.contexto = contexto;

        // Al cambiar de formato se recalcula si se puede copiar sin recomprimir.
        formato.addListener((obs, antes, ahora) -> ajustarRemux());
        soloAudio.addListener((obs, antes, ahora) -> ajustarRemux());
        seleccionado.addListener((obs, antes, ahora) -> {
            ajustarRemux();
            if (ahora != null) {
                pistaAudio.set(ahora.primeraDeAudio().orElse(null));
            }
        });
    }

    /** Añade archivos a la lista y los analiza en segundo plano. */
    public void anadir(List<Path> rutas, Runnable alTerminar) {
        List<Path> nuevos = rutas.stream()
                .filter(r -> archivos.stream().noneMatch(a -> a.archivo().equals(r)))
                .toList();
        if (nuevos.isEmpty()) {
            alTerminar.run();
            return;
        }
        if (contexto.rutaFfmpeg().isEmpty()) {
            error.set(new ErrorAmistoso(Textos.t("comun.no_se_encuentra_ffmpeg"),
                    Textos.t("descargar.abrelo_desde_ajustes")));
            alTerminar.run();
            return;
        }

        analizando.set(true);
        error.set(null);

        EnSegundoPlano.calcular(contexto.tareas(), () -> analizarTodos(nuevos),
                leidos -> {
                    analizando.set(false);
                    archivos.addAll(leidos);
                    if (seleccionado.get() == null && !archivos.isEmpty()) {
                        seleccionado.set(archivos.get(0));
                    }
                    if (leidos.size() < nuevos.size()) {
                        error.set(new ErrorAmistoso(
                                Textos.t("convertir.no_se_han_podido_leer",
                                        nuevos.size() - leidos.size(), nuevos.size()),
                                Textos.t("convertir.mira_la_consola_cuales")));
                    }
                    alTerminar.run();
                },
                causa -> {
                    analizando.set(false);
                    error.set(new ErrorAmistoso(Textos.t("convertir.no_se_han_podido_analizar"),
                            Textos.t("comun.consulta_la_consola"), String.valueOf(causa)));
                    alTerminar.run();
                });
    }

    /** Lee los archivos uno a uno; los que fallen se apuntan en la consola y se saltan. */
    private List<MediaInfo> analizarTodos(List<Path> rutas) {
        List<MediaInfo> leidos = new ArrayList<>();
        for (Path ruta : rutas) {
            try {
                leidos.add(contexto.probe().analizar(ruta));
                contexto.consola().registrar("ffprobe",
                        Textos.t("convertir.consola_analizado", ruta));
            } catch (AppException e) {
                log.warn("No se ha podido analizar {}", ruta, e);
                contexto.consola().registrar("ffprobe", NivelConsola.ERROR,
                        ruta + ": " + e.error().mensaje());
            }
        }
        return leidos;
    }

    public void quitar(MediaInfo archivo) {
        archivos.remove(archivo);
        if (seleccionado.get() == archivo) {
            seleccionado.set(archivos.isEmpty() ? null : archivos.get(0));
        }
    }

    public void limpiar() {
        archivos.clear();
        seleccionado.set(null);
    }

    /**
     * Pone el remux al día: solo se puede copiar sin recomprimir si el contenedor de destino
     * admite los códecs que ya tiene el archivo.
     */
    private void ajustarRemux() {
        if (!puedeRemuxear().posible()) {
            remuxear.set(false);
        }
    }

    /** ¿Se puede copiar sin recomprimir el archivo seleccionado al formato elegido? */
    public RemuxDecider.Resultado puedeRemuxear() {
        MediaInfo info = seleccionado.get();
        if (info == null) {
            return RemuxDecider.Resultado.no(Textos.t("convertir.ningun_archivo_seleccionado"));
        }
        return RemuxDecider.comprobar(info, formato.get(), soloAudio.get());
    }

    /** Perfil de calidad que se va a aplicar, con la variante por GPU si procede. */
    public PerfilCalidad perfil() {
        PerfilCalidad base = Calidades.de(formato.get(), nivel.get());
        return usarGpu.get() ? Calidades.conNvenc(base) : base;
    }

    /** Avisos de «no inventes calidad» para el archivo seleccionado. */
    public List<String> avisosDeCalidad() {
        MediaInfo info = seleccionado.get();
        if (info == null) {
            return List.of();
        }
        PistaMedia video = info.primeraDeVideo().orElse(null);
        PistaMedia audio = info.primeraDeAudio().orElse(null);

        var origen = new ValidadorCalidad.Origen(
                video != null ? video.ancho() : 0,
                video != null ? video.alto() : 0,
                video != null ? video.fps() : 0,
                audio != null ? audio.frecuenciaHz() : 0,
                // La profundidad solo cuenta si el origen la guarda de verdad; en un códec con
                // pérdida es el formato interno del decodificador y no significa nada.
                audio != null && audio.sinPerdida() ? audio.bitsEfectivos() : 0,
                audio != null ? audio.canales() : 0,
                audio != null && !audio.sinPerdida());

        var destino = new ValidadorCalidad.Destino(
                entero(ancho), entero(alto), decimal(fps),
                entero(frecuencia), 0, entero(canales));

        return remuxear.get()
                ? List.of()
                : ValidadorCalidad.comprobar(origen, destino, perfil());
    }

    /** Tamaño estimado del resultado del archivo seleccionado. */
    public long tamanoEstimado() {
        return tamanoEstimado(seleccionado.get());
    }

    /** Suma de los tamaños estimados de toda la lista, para el resumen de abajo. */
    public long tamanoEstimadoTotal() {
        long total = 0;
        for (MediaInfo info : archivos) {
            total += tamanoEstimado(info);
        }
        return total;
    }

    private long tamanoEstimado(MediaInfo info) {
        if (info == null) {
            return 0;
        }
        if (remuxear.get()) {
            // Remuxear no cambia los datos: el tamaño es casi el mismo.
            return info.tamanoBytes();
        }
        long segundos = duracionSalida(info).getSeconds();
        PerfilCalidad perfil = perfil();
        if (formato.get().esAudio() || soloAudio.get()) {
            PistaMedia audio = info.primeraDeAudio().orElse(null);
            return Calidades.tamanoEstimado(perfil, segundos,
                    audio != null ? audio.canales() : 2,
                    audio != null ? audio.frecuenciaHz() : 0);
        }
        // En vídeo el tamaño depende del contenido; se usa el bitrate del original como guía.
        if (info.bitrate() > 0 && segundos > 0) {
            double factor = switch (nivel.get()) {
                case MAXIMA -> perfil.sinPerdida() ? 8.0 : 1.5;
                case MEDIA -> 0.8;
                case MINIMA -> 0.3;
            };
            return (long) (info.bitrate() / 8.0 * segundos * factor);
        }
        return 0;
    }

    private Duration duracionSalida(MediaInfo info) {
        Duration desde = tiempo(inicio).orElse(Duration.ZERO);
        Optional<Duration> hasta = tiempo(fin);
        Duration total = info.duracion() != null ? info.duracion() : Duration.ZERO;
        Duration limite = hasta.orElse(total);
        Duration resultado = limite.minus(desde);
        return resultado.isNegative() ? Duration.ZERO : resultado;
    }

    /**
     * Construye las peticiones de todos los archivos. El recorte y la pista de audio solo se
     * aplican con un único archivo: en un lote no tendría sentido recortarlos todos igual.
     */
    public List<ConversionRequest> construirPeticiones() {
        List<ConversionRequest> peticiones = new ArrayList<>();
        boolean loteDeVarios = archivos.size() > 1;

        for (MediaInfo info : archivos) {
            boolean puedeCopiar = RemuxDecider.comprobar(info, formato.get(), soloAudio.get())
                    .posible();
            String nombre = FilenameTemplate.asegurarUnico(destino.get(),
                    FilenameTemplate.sanear(sinExtension(info.nombreArchivo())),
                    formato.get().extension());

            ConversionRequest.Builder b = ConversionRequest.builder()
                    .origen(info.archivo())
                    .info(info)
                    .formato(formato.get())
                    .nivel(nivel.get())
                    .remuxear(remuxear.get() && puedeCopiar)
                    .usarGpu(usarGpu.get())
                    .soloAudio(soloAudio.get())
                    .carpetaDestino(destino.get())
                    .nombreBase(nombre);

            if (!loteDeVarios) {
                b.pistaAudio(pistaAudio.get() != null ? pistaAudio.get().indiceEnTipo() : -1)
                        .resolucion(entero(ancho), entero(alto))
                        .fps(decimal(fps))
                        .frecuencia(entero(frecuencia))
                        .canales(entero(canales))
                        .recorte(tiempo(inicio).orElse(null), tiempo(fin).orElse(null));
            }
            peticiones.add(b.build());
        }
        return peticiones;
    }

    private static String sinExtension(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return punto > 0 ? nombre.substring(0, punto) : nombre;
    }

    private static int entero(StringProperty propiedad) {
        try {
            String valor = propiedad.get();
            return valor == null || valor.isBlank() ? 0 : Integer.parseInt(valor.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double decimal(StringProperty propiedad) {
        try {
            String valor = propiedad.get();
            return valor == null || valor.isBlank()
                    ? 0 : Double.parseDouble(valor.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Acepta 90, 1:30 y 0:01:30. */
    static Optional<Duration> tiempo(StringProperty propiedad) {
        String valor = propiedad.get();
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String[] partes = valor.strip().split(":");
        try {
            long segundos = 0;
            for (String parte : partes) {
                segundos = segundos * 60 + Long.parseLong(parte.strip());
            }
            return Optional.of(Duration.ofSeconds(segundos));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ propiedades

    public ObservableList<MediaInfo> archivos() {
        return archivos;
    }

    public ObjectProperty<MediaInfo> seleccionadoProperty() {
        return seleccionado;
    }

    public ObjectProperty<FormatoSalida> formatoProperty() {
        return formato;
    }

    public ObjectProperty<NivelCalidad> nivelProperty() {
        return nivel;
    }

    public BooleanProperty remuxearProperty() {
        return remuxear;
    }

    public BooleanProperty soloAudioProperty() {
        return soloAudio;
    }

    public BooleanProperty usarGpuProperty() {
        return usarGpu;
    }

    public ObjectProperty<PistaMedia> pistaAudioProperty() {
        return pistaAudio;
    }

    public StringProperty anchoProperty() {
        return ancho;
    }

    public StringProperty altoProperty() {
        return alto;
    }

    public StringProperty fpsProperty() {
        return fps;
    }

    public StringProperty frecuenciaProperty() {
        return frecuencia;
    }

    public StringProperty canalesProperty() {
        return canales;
    }

    public StringProperty inicioProperty() {
        return inicio;
    }

    public StringProperty finProperty() {
        return fin;
    }

    /** De dónde sale la carpeta de destino al construir las peticiones. */
    public void destino(Supplier<Path> carpeta) {
        this.destino = carpeta != null ? carpeta : () -> null;
    }

    public BooleanProperty analizandoProperty() {
        return analizando;
    }

    public ObjectProperty<ErrorAmistoso> errorProperty() {
        return error;
    }

    public Contexto contexto() {
        return contexto;
    }
}
