package com.ultimatedownloader.model.convert;

import com.ultimatedownloader.model.calidad.FormatoSalida;
import com.ultimatedownloader.model.calidad.NivelCalidad;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Una conversión concreta: de qué archivo, a qué formato y con qué opciones. Inmutable, como
 * la petición de descarga. Quien la traduce a argumentos es FfmpegArgumentBuilder.
 *
 * Lo que vaya a 0 o a null es «dejarlo como está».
 */
public record ConversionRequest(
        Path origen,
        MediaInfo info,
        FormatoSalida formato,
        NivelCalidad nivel,
        boolean remuxear,
        boolean usarGpu,
        boolean soloAudio,
        Path carpetaDestino,
        String nombreBase,
        int pistaAudio,
        int ancho,
        int alto,
        double fps,
        int frecuenciaHz,
        int canales,
        Duration inicio,
        Duration fin) {

    public ConversionRequest {
        Objects.requireNonNull(origen, "origen");
        Objects.requireNonNull(formato, "formato");
        Objects.requireNonNull(carpetaDestino, "carpetaDestino");
        nivel = nivel != null ? nivel : NivelCalidad.MEDIA;
    }

    /** Archivo que se va a escribir. */
    public Path destino() {
        return carpetaDestino.resolve(nombreBase + "." + formato.extension());
    }

    /** true si hay que recortar por tiempo. */
    public boolean hayRecorte() {
        return (inicio != null && !inicio.isZero()) || fin != null;
    }

    /** Duración del trozo que se va a convertir, o null si es el archivo entero. */
    public Duration duracionRecorte() {
        if (fin == null) {
            return null;
        }
        Duration desde = inicio != null ? inicio : Duration.ZERO;
        Duration trozo = fin.minus(desde);
        return trozo.isNegative() || trozo.isZero() ? null : trozo;
    }

    /** Duración que tendrá el resultado, para calcular el porcentaje de avance. */
    public Duration duracionSalida() {
        Duration recorte = duracionRecorte();
        if (recorte != null) {
            return recorte;
        }
        Duration total = info != null ? info.duracion() : null;
        if (total == null) {
            return Duration.ZERO;
        }
        return inicio != null ? total.minus(inicio) : total;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copia() {
        return new Builder()
                .origen(origen).info(info).formato(formato).nivel(nivel)
                .remuxear(remuxear).usarGpu(usarGpu).soloAudio(soloAudio)
                .carpetaDestino(carpetaDestino).nombreBase(nombreBase)
                .pistaAudio(pistaAudio)
                .resolucion(ancho, alto).fps(fps)
                .frecuencia(frecuenciaHz).canales(canales)
                .recorte(inicio, fin);
    }

    /** Constructor fluido: la petición tiene demasiados campos para un constructor normal. */
    public static final class Builder {

        private Path origen;
        private MediaInfo info;
        private FormatoSalida formato;
        private NivelCalidad nivel = NivelCalidad.MEDIA;
        private boolean remuxear;
        private boolean usarGpu;
        private boolean soloAudio;
        private Path carpetaDestino;
        private String nombreBase;
        private int pistaAudio = -1;
        private int ancho;
        private int alto;
        private double fps;
        private int frecuenciaHz;
        private int canales;
        private Duration inicio;
        private Duration fin;

        public Builder origen(Path valor) {
            this.origen = valor;
            return this;
        }

        public Builder info(MediaInfo valor) {
            this.info = valor;
            return this;
        }

        public Builder formato(FormatoSalida valor) {
            this.formato = valor;
            return this;
        }

        public Builder nivel(NivelCalidad valor) {
            this.nivel = valor;
            return this;
        }

        public Builder remuxear(boolean valor) {
            this.remuxear = valor;
            return this;
        }

        public Builder usarGpu(boolean valor) {
            this.usarGpu = valor;
            return this;
        }

        public Builder soloAudio(boolean valor) {
            this.soloAudio = valor;
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

        public Builder pistaAudio(int valor) {
            this.pistaAudio = valor;
            return this;
        }

        public Builder resolucion(int ancho, int alto) {
            this.ancho = ancho;
            this.alto = alto;
            return this;
        }

        public Builder fps(double valor) {
            this.fps = valor;
            return this;
        }

        public Builder frecuencia(int valor) {
            this.frecuenciaHz = valor;
            return this;
        }

        public Builder canales(int valor) {
            this.canales = valor;
            return this;
        }

        public Builder recorte(Duration inicio, Duration fin) {
            this.inicio = inicio;
            this.fin = fin;
            return this;
        }

        public ConversionRequest build() {
            return new ConversionRequest(origen, info, formato, nivel, remuxear, usarGpu,
                    soloAudio, carpetaDestino, nombreBase, pistaAudio, ancho, alto, fps,
                    frecuenciaHz, canales, inicio, fin);
        }
    }
}
