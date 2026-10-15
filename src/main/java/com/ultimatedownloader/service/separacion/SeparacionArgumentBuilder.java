package com.ultimatedownloader.service.separacion;

import com.ultimatedownloader.model.calidad.Calidades;
import com.ultimatedownloader.model.calidad.PerfilCalidad;
import com.ultimatedownloader.model.separacion.ModeloUvr;
import com.ultimatedownloader.model.separacion.OpcionesSeparacion;
import com.ultimatedownloader.model.separacion.SeparacionRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Construye la orden de audio-separator, siempre como lista de argumentos. Lo que no se ha
 * tocado no se pasa, para que mande el valor que trae cada modelo.
 */
public final class SeparacionArgumentBuilder {

    private final Path ejecutable;
    private final Path carpetaModelos;

    public SeparacionArgumentBuilder(Path ejecutable, Path carpetaModelos) {
        this.ejecutable = ejecutable;
        this.carpetaModelos = carpetaModelos;
    }

    /**
     * La entrada no siempre es el archivo original: puede ser un temporal recortado o el audio
     * sacado de un vídeo.
     */
    public List<String> construir(SeparacionRequest peticion, Path entrada) {
        List<String> orden = new ArrayList<>();
        orden.add(ejecutable.toString());
        orden.add(entrada.toString());

        orden.add("--model_file_dir");
        orden.add(carpetaModelos.toString());
        orden.add("--output_dir");
        orden.add(peticion.carpetaDestino().toString());

        orden.add("-m");
        orden.add(peticion.modeloPrincipal());

        if (peticion.esMezcla()) {
            orden.add("--extra_models");
            orden.addAll(peticion.modelosExtra());
            if (peticion.algoritmoEnsemble() != null) {
                orden.add("--ensemble_algorithm");
                orden.add(peticion.algoritmoEnsemble());
            }
            if (!peticion.pesosEnsemble().isEmpty()) {
                orden.add("--ensemble_weights");
                peticion.pesosEnsemble().forEach(peso -> orden.add(numero(peso)));
            }
        }

        anadirSalida(orden, peticion);
        anadirOpciones(orden, peticion.opciones());
        return List.copyOf(orden);
    }

    /** Orden que solo descarga el modelo, sin separar nada. */
    public List<String> descargarModelo(String fichero) {
        return List.of(ejecutable.toString(),
                "--model_file_dir", carpetaModelos.toString(),
                "-m", fichero,
                "--download_model_only");
    }

    /** Orden que pide el catálogo en JSON. */
    public List<String> listarModelos() {
        return List.of(ejecutable.toString(),
                "--model_file_dir", carpetaModelos.toString(),
                "--list_models", "--list_format", "json");
    }

    // ------------------------------------------------------------------ interno

    private static void anadirSalida(List<String> orden, SeparacionRequest peticion) {
        orden.add("--output_format");
        orden.add(peticion.formato().extension().toUpperCase(Locale.ROOT));

        // El motor no acepta argumentos de ffmpeg, solo formato y bitrate: de la tabla de
        // calidades se saca el bitrate y el resto (sin pérdida, compresión) lo decide el formato.
        PerfilCalidad perfil = Calidades.de(peticion.formato(), peticion.nivel());
        if (!perfil.sinPerdida() && perfil.bitrateKbps() > 0) {
            orden.add("--output_bitrate");
            orden.add(perfil.bitrateKbps() + "k");
        }

        if (peticion.frecuenciaHz() > 0) {
            orden.add("--sample_rate");
            orden.add(String.valueOf(peticion.frecuenciaHz()));
        }

        String pista = ModeloUvr.claveParaSingleStem(peticion.pistaUnica());
        if (pista != null) {
            orden.add("--single_stem");
            orden.add(pista);
        }
    }

    private static void anadirOpciones(List<String> orden, OpcionesSeparacion o) {
        valor(orden, "--normalization", o.normalizacion());
        valor(orden, "--amplification", o.amplificacion());
        bandera(orden, "--invert_spect", o.invertirEspectro());
        bandera(orden, "--use_autocast", o.usarAutocast());
        bandera(orden, "--use_native_fp16", o.usarFp16());
        bandera(orden, "--use_torch_compile", o.usarTorchCompile());
        valor(orden, "--chunk_duration", o.duracionTrozo());

        valor(orden, "--mdx_segment_size", o.mdxTamanoSegmento());
        valor(orden, "--mdx_overlap", o.mdxSolapamiento());
        valor(orden, "--mdx_batch_size", o.mdxLote());
        valor(orden, "--mdx_hop_length", o.mdxHop());
        bandera(orden, "--mdx_enable_denoise", o.mdxDenoise());

        valor(orden, "--vr_batch_size", o.vrLote());
        valor(orden, "--vr_window_size", o.vrVentana());
        valor(orden, "--vr_aggression", o.vrAgresividad());
        bandera(orden, "--vr_enable_tta", o.vrTta());
        bandera(orden, "--vr_high_end_process", o.vrAgudos());
        bandera(orden, "--vr_enable_post_process", o.vrPostProceso());
        valor(orden, "--vr_post_process_threshold", o.vrUmbralPostProceso());

        valor(orden, "--demucs_segment_size", o.demucsSegmento());
        valor(orden, "--demucs_shifts", o.demucsShifts());
        valor(orden, "--demucs_overlap", o.demucsSolapamiento());

        valor(orden, "--mdxc_segment_size", o.mdxcSegmento());
        bandera(orden, "--mdxc_override_model_segment_size", o.mdxcForzarSegmento());
        valor(orden, "--mdxc_overlap", o.mdxcSolapamiento());
        valor(orden, "--mdxc_batch_size", o.mdxcLote());
        valor(orden, "--mdxc_pitch_shift", o.mdxcTono());
    }

    private static void valor(List<String> orden, String nombre, Integer valor) {
        if (valor != null) {
            orden.add(nombre);
            orden.add(String.valueOf(valor.intValue()));
        }
    }

    private static void valor(List<String> orden, String nombre, Double valor) {
        if (valor != null) {
            orden.add(nombre);
            orden.add(numero(valor));
        }
    }

    private static void bandera(List<String> orden, String nombre, boolean activa) {
        if (activa) {
            orden.add(nombre);
        }
    }

    /** Los números van siempre con punto decimal: es una línea de órdenes, no texto para leer. */
    private static String numero(double valor) {
        if (valor == Math.rint(valor) && !Double.isInfinite(valor)) {
            return String.valueOf((long) valor);
        }
        return String.format(Locale.ROOT, "%s", valor);
    }
}
