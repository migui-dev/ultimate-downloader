package com.ultimatedownloader.service;

import com.ultimatedownloader.model.AudioCodec;
import com.ultimatedownloader.model.CompatibilidadContenedor;
import com.ultimatedownloader.model.DownloadMode;
import com.ultimatedownloader.model.VideoCodec;
import com.ultimatedownloader.model.VideoContainer;

/**
 * Decide si el contenedor elegido admite los códecs seleccionados. Lo normal es remuxear, que
 * no pierde calidad; cuando el contenedor no acepta esos códecs se avisa y se ofrece
 * recodificar a H.264 + AAC, que lo abre cualquier editor.
 */
public final class ContainerCompatibility {

    private ContainerCompatibility() {
    }

    /** El modo de descarga entra para no avisar de pistas que no se van a descargar. */
    public static CompatibilidadContenedor comprobar(VideoContainer contenedor, VideoCodec video,
                                                     AudioCodec audio, DownloadMode modo) {
        if (contenedor == null || modo == DownloadMode.SOLO_AUDIO) {
            return CompatibilidadContenedor.COMPATIBLE;
        }
        VideoCodec v = modo.incluyeVideo() && video != null ? video : VideoCodec.NINGUNO;
        AudioCodec a = modo.incluyeAudio() && audio != null ? audio : AudioCodec.NINGUNO;

        boolean videoOk = contenedor.admite(v);
        boolean audioOk = contenedor.admite(a);
        if (videoOk && audioOk) {
            return CompatibilidadContenedor.COMPATIBLE;
        }

        String aviso;
        if (!videoOk && !audioOk) {
            aviso = "El contenedor " + contenedor.nombreLegible() + " no admite vídeo "
                    + v.nombreLegible() + " ni audio " + a.nombreLegible() + ".";
        } else if (!videoOk) {
            aviso = "El contenedor " + contenedor.nombreLegible() + " no admite vídeo "
                    + v.nombreLegible() + ".";
        } else {
            aviso = "El contenedor " + contenedor.nombreLegible() + " no admite audio "
                    + a.nombreLegible() + ".";
        }

        VideoContainer alternativa = contenedorSinRecomprimir(v, a);
        String sugerencia = alternativa != null
                ? "Cambia el contenedor a " + alternativa.nombreLegible()
                        + " para guardarlo sin recomprimir, o recodifica a H.264 + AAC."
                : "Recodifica a H.264 + AAC (tarda más, pero es compatible con cualquier editor).";

        return new CompatibilidadContenedor(false, aviso, sugerencia);
    }

    /** Primer contenedor que admite ambos códecs sin recomprimir, o null si no hay. */
    public static VideoContainer contenedorSinRecomprimir(VideoCodec video, AudioCodec audio) {
        for (VideoContainer c : VideoContainer.values()) {
            if (c.admite(video) && c.admite(audio)) {
                return c;
            }
        }
        return null;
    }
}
