package com.ultimatedownloader.model.separacion;

import com.ultimatedownloader.model.calidad.FormatoSalida;
import com.ultimatedownloader.model.calidad.NivelCalidad;
import java.nio.file.Path;
import java.util.List;

/** Todo lo que hace falta para separar un archivo, que puede ser audio o vídeo. */
public record SeparacionRequest(Path entrada, Path carpetaDestino, List<String> modelos,
                                ModoVariosModelos modo, String algoritmoEnsemble,
                                List<Double> pesosEnsemble, String pistaUnica,
                                FormatoSalida formato, NivelCalidad nivel, int frecuenciaHz,
                                OpcionesSeparacion opciones, Muestra muestra,
                                boolean reemplazarEnVideo) {

    public SeparacionRequest {
        modelos = modelos == null ? List.of() : List.copyOf(modelos);
        pesosEnsemble = pesosEnsemble == null ? List.of() : List.copyOf(pesosEnsemble);
        if (modelos.isEmpty()) {
            throw new IllegalArgumentException("Hace falta al menos un modelo");
        }
        if (opciones == null) {
            opciones = OpcionesSeparacion.porDefecto();
        }
    }

    /** Qué hacer cuando se eligen varios modelos. */
    public enum ModoVariosModelos {

        /** Se ejecuta cada modelo por su cuenta y se guardan todos los resultados. */
        POR_SEPARADO,

        /** Se combinan en una sola salida con el ensemble que trae el propio motor. */
        MEZCLA
    }

    /** Trozo que se va a separar. El motor no sabe recortar, así que lo cortamos con ffmpeg. */
    public record Muestra(int desdeSegundo, int segundos) {

        public Muestra {
            if (segundos <= 0) {
                throw new IllegalArgumentException("La muestra tiene que durar algo");
            }
            if (desdeSegundo < 0) {
                throw new IllegalArgumentException("La muestra no puede empezar antes del principio");
            }
        }
    }

    /** Modelo principal, que es el que manda en el nombre de los archivos y en el ensemble. */
    public String modeloPrincipal() {
        return modelos.get(0);
    }

    /** Modelos adicionales, los que van en --extra_models. */
    public List<String> modelosExtra() {
        return modelos.subList(1, modelos.size());
    }

    public boolean esMezcla() {
        return modo == ModoVariosModelos.MEZCLA && modelos.size() > 1;
    }
}
