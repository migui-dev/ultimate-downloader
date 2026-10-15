package com.ultimatedownloader.service;

import com.ultimatedownloader.model.AudioFormat;
import com.ultimatedownloader.model.AudioOutputFormat;
import com.ultimatedownloader.model.DownloadMode;
import com.ultimatedownloader.model.EstimacionTamano;
import com.ultimatedownloader.model.VideoFormat;
import java.time.Duration;

/**
 * Estima el tamaño de la combinación elegida. yt-dlp da filesize en los formatos por HTTPS
 * pero no en los HLS; cuando falta se calcula con el bitrate y la duración. Si hay conversión
 * de audio, manda el bitrate del formato de salida.
 */
public final class SizeEstimator {

    private SizeEstimator() {
    }

    public static EstimacionTamano estimar(VideoFormat video, AudioFormat audio, Duration duracion,
                                           DownloadMode modo, AudioOutputFormat formatoAudio,
                                           boolean recodificar) {
        long segundos = duracion != null ? Math.max(0, duracion.getSeconds()) : 0;

        if (modo == DownloadMode.SOLO_AUDIO) {
            return estimarAudio(audio, segundos, formatoAudio);
        }

        long total = 0;
        boolean aproximado = false;

        if (video == null) {
            return EstimacionTamano.DESCONOCIDA;
        }
        long tamanoVideo = tamanoDe(video.tamanoBytes(), video.bitrateKbps(), segundos);
        if (tamanoVideo <= 0) {
            return EstimacionTamano.DESCONOCIDA;
        }
        total += tamanoVideo;
        aproximado |= !video.tamanoExacto() || video.tamanoBytes() == null;

        if (modo == DownloadMode.VIDEO_Y_AUDIO && !video.incluyeAudio()) {
            if (audio == null) {
                return new EstimacionTamano(total, true);
            }
            long tamanoAudio = tamanoDe(audio.tamanoBytes(), audio.bitrateKbps(), segundos);
            if (tamanoAudio <= 0) {
                aproximado = true;
            } else {
                total += tamanoAudio;
                aproximado |= !audio.tamanoExacto() || audio.tamanoBytes() == null;
            }
        }

        // Al recodificar el tamaño final depende del CRF, así que solo puede ser orientativo.
        return new EstimacionTamano(total, aproximado || recodificar);
    }

    private static EstimacionTamano estimarAudio(AudioFormat audio, long segundos,
                                                 AudioOutputFormat formato) {
        AudioOutputFormat salida = formato != null ? formato : AudioOutputFormat.ORIGINAL;
        if (salida.requiereConversion() && segundos > 0) {
            long bytes = (long) (salida.bitrateAproximadoKbps() * 1000.0 / 8.0 * segundos);
            return new EstimacionTamano(bytes, true);
        }
        if (audio == null) {
            return EstimacionTamano.DESCONOCIDA;
        }
        long bytes = tamanoDe(audio.tamanoBytes(), audio.bitrateKbps(), segundos);
        if (bytes <= 0) {
            return EstimacionTamano.DESCONOCIDA;
        }
        return new EstimacionTamano(bytes, !audio.tamanoExacto() || audio.tamanoBytes() == null);
    }

    /** Usa el tamaño declarado y, si no lo hay, lo calcula con el bitrate y la duración. */
    private static long tamanoDe(Long declarado, double bitrateKbps, long segundos) {
        if (declarado != null && declarado > 0) {
            return declarado;
        }
        if (bitrateKbps > 0 && segundos > 0) {
            return (long) (bitrateKbps * 1000.0 / 8.0 * segundos);
        }
        return 0;
    }
}
