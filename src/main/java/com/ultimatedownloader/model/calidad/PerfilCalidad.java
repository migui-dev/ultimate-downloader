package com.ultimatedownloader.model.calidad;

import java.util.List;
import java.util.stream.Stream;

/**
 * Parámetros exactos de un formato con un nivel de calidad.
 *
 * Los argumentos de vídeo y los de audio van separados a propósito: al extraer solo el audio
 * de un vídeo hay que quedarse con los segundos y descartar los primeros.
 */
public record PerfilCalidad(
        FormatoSalida formato,
        NivelCalidad nivel,
        List<String> argumentosVideo,
        List<String> argumentosAudio,
        String resumen,
        int bitrateKbps,
        boolean sinPerdida) {

    public PerfilCalidad {
        argumentosVideo = List.copyOf(argumentosVideo);
        argumentosAudio = List.copyOf(argumentosAudio);
    }

    /** Todos los argumentos, primero los de vídeo y después los de audio. */
    public List<String> argumentos() {
        return Stream.concat(argumentosVideo.stream(), argumentosAudio.stream()).toList();
    }

    /** Texto para el desplegable: «FLAC · sin pérdida, 24 bits». */
    public String etiqueta() {
        return formato.nombreLegible() + " · " + resumen;
    }
}
