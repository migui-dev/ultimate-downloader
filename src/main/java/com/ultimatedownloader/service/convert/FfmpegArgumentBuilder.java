package com.ultimatedownloader.service.convert;

import com.ultimatedownloader.model.calidad.Calidades;
import com.ultimatedownloader.model.calidad.FormatoSalida;
import com.ultimatedownloader.model.calidad.PerfilCalidad;
import com.ultimatedownloader.model.convert.ConversionRequest;
import com.ultimatedownloader.model.convert.TipoPista;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Construye la orden de ffmpeg para una conversión. Cada argumento es un elemento de la lista,
 * nunca una cadena concatenada, así que las rutas raras llegan intactas.
 *
 * Los parámetros de codificación no se deciden aquí: salen de Calidades.
 */
public final class FfmpegArgumentBuilder {

    private final Path ffmpeg;

    public FfmpegArgumentBuilder(Path ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    /** Orden completa para una conversión. */
    public List<String> construir(ConversionRequest p) {
        List<String> args = new ArrayList<>();
        args.add(ffmpeg.toString());
        args.add("-hide_banner");
        // Sin esto, ffmpeg se queda esperando en el teclado si el archivo de salida existe.
        args.add("-nostdin");
        args.add("-y");
        // Progreso en pares clave=valor por la salida estándar, en vez de la barra para humanos.
        args.add("-progress");
        args.add("pipe:1");
        args.add("-nostats");
        args.add("-loglevel");
        args.add("info");

        // El salto va antes de -i: así ffmpeg busca el punto sin decodificar lo anterior.
        if (p.inicio() != null && !p.inicio().isZero()) {
            args.add("-ss");
            args.add(enSegundos(p.inicio()));
        }
        args.add("-i");
        args.add(p.origen().toString());

        Duration trozo = p.duracionRecorte();
        if (trozo != null) {
            args.add("-t");
            args.add(enSegundos(trozo));
        }

        anadirMapeos(args, p);

        if (p.remuxear()) {
            // Copiar las pistas tal cual: ni se decodifica ni se vuelve a comprimir.
            args.add("-c");
            args.add("copy");
            if (p.formato() == FormatoSalida.MP4) {
                args.add("-movflags");
                args.add("+faststart");
            }
        } else {
            anadirCodificacion(args, p);
        }

        // Los metadatos y las portadas del original se conservan siempre que se pueda.
        args.add("-map_metadata");
        args.add("0");

        args.add(p.destino().toString());
        return args;
    }

    /**
     * Qué pistas entran en el resultado: la pista de audio elegida o la primera, y los subtítulos
     * solo si el destino es Matroska, que es el único que los admite sin complicaciones.
     */
    private void anadirMapeos(List<String> args, ConversionRequest p) {
        boolean conVideo = !p.soloAudio() && p.formato().esVideo()
                && p.info() != null && p.info().tieneVideo();

        if (conVideo) {
            args.add("-map");
            args.add("0:v:0");
        }
        args.add("-map");
        args.add("0:a:" + Math.max(0, p.pistaAudio()));

        if (conVideo && p.formato() == FormatoSalida.MKV
                && p.info() != null
                && !p.info().pistasDe(TipoPista.SUBTITULO).isEmpty()) {
            args.add("-map");
            args.add("0:s?");
        }
        if (!conVideo) {
            // Sin esto, ffmpeg arrastra la portada como una pista de vídeo y rompe la salida.
            args.add("-vn");
        }
    }

    private void anadirCodificacion(List<String> args, ConversionRequest p) {
        PerfilCalidad perfil = Calidades.de(p.formato(), p.nivel());
        if (p.usarGpu()) {
            perfil = Calidades.conNvenc(perfil);
        }

        boolean conVideo = !p.soloAudio() && p.formato().esVideo();
        if (conVideo) {
            args.addAll(perfil.argumentosVideo());
        }
        args.addAll(perfil.argumentosAudio());

        if (conVideo && p.ancho() > 0 && p.alto() > 0) {
            args.add("-vf");
            args.add("scale=" + p.ancho() + ":" + p.alto() + ":flags=lanczos");
        }
        if (conVideo && p.fps() > 0) {
            args.add("-r");
            args.add(numero(p.fps()));
        }
        if (p.frecuenciaHz() > 0) {
            args.add("-ar");
            args.add(String.valueOf(p.frecuenciaHz()));
        }
        if (p.canales() > 0) {
            args.add("-ac");
            args.add(String.valueOf(p.canales()));
        }
    }

    /** ffmpeg acepta los tiempos en segundos con decimales. */
    static String enSegundos(Duration duracion) {
        return String.format(Locale.ROOT, "%.3f", duracion.toMillis() / 1000.0);
    }

    private static String numero(double valor) {
        return Math.abs(valor - Math.rint(valor)) < 0.001
                ? String.valueOf(Math.round(valor))
                : String.format(Locale.ROOT, "%.3f", valor);
    }
}
