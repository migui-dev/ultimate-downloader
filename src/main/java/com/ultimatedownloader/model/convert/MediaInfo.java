package com.ultimatedownloader.model.convert;

import com.ultimatedownloader.util.Formatos;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Lo que ffprobe sabe de un archivo: contenedor, duración, tamaño, bitrate y sus pistas. */
public record MediaInfo(
        Path archivo,
        String contenedor,
        Duration duracion,
        long tamanoBytes,
        long bitrate,
        List<PistaMedia> pistas) {

    public MediaInfo {
        pistas = List.copyOf(pistas);
    }

    public List<PistaMedia> pistasDe(TipoPista tipo) {
        return pistas.stream().filter(p -> p.tipo() == tipo).toList();
    }

    public Optional<PistaMedia> primeraDeVideo() {
        return pistasDe(TipoPista.VIDEO).stream().findFirst();
    }

    public Optional<PistaMedia> primeraDeAudio() {
        return pistasDe(TipoPista.AUDIO).stream().findFirst();
    }

    /** true si el archivo no tiene imagen: es música o una grabación de voz. */
    public boolean esSoloAudio() {
        return pistasDe(TipoPista.VIDEO).isEmpty() && !pistasDe(TipoPista.AUDIO).isEmpty();
    }

    public boolean tieneVideo() {
        return !pistasDe(TipoPista.VIDEO).isEmpty();
    }

    public String nombreArchivo() {
        return archivo != null ? archivo.getFileName().toString() : "";
    }

    /** Extensión del archivo, en minúsculas y sin el punto. */
    public String extension() {
        String nombre = nombreArchivo();
        int punto = nombre.lastIndexOf('.');
        return punto >= 0 ? nombre.substring(punto + 1).toLowerCase(java.util.Locale.ROOT) : "";
    }

    public String duracionTexto() {
        return Formatos.duracionReloj(duracion != null ? duracion.getSeconds() : 0);
    }

    /** Resumen de una línea: contenedor, duración, tamaño y bitrate. */
    public String resumen() {
        StringBuilder sb = new StringBuilder();
        sb.append(extension().toUpperCase(java.util.Locale.ROOT));
        if (duracion != null && !duracion.isZero()) {
            sb.append(" · ").append(duracionTexto());
        }
        if (tamanoBytes > 0) {
            sb.append(" · ").append(Formatos.tamano(tamanoBytes));
        }
        if (bitrate > 0) {
            sb.append(" · ").append(Formatos.bitrate(bitrate / 1000.0));
        }
        return sb.toString();
    }
}
