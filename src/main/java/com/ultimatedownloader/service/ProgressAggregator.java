package com.ultimatedownloader.service;

import com.ultimatedownloader.model.DownloadPhase;
import com.ultimatedownloader.model.ProgressSnapshot;

/**
 * Convierte el progreso por pista de yt-dlp en uno global. Vídeo + audio son dos descargas
 * seguidas de 0 a 100 %: para que la barra avance una sola vez se suman los bytes de las
 * pistas terminadas y se usa el tamaño estimado como total.
 *
 * Cuando no hay estimación —pistas HLS, que no traen tamaño— se cuenta por pistas: la primera
 * ocupa la primera mitad de la barra y la segunda el resto. Sin eso, la barra llegaba al 100 %
 * al acabar el vídeo y volvía atrás al empezar el audio.
 *
 * Cada descarga tiene el suyo y las líneas llegan desde un solo hilo, así que no hace falta
 * sincronizar nada.
 */
public final class ProgressAggregator {

    private final long totalEstimado;
    private final int pistasPrevistas;

    private String formatoActual;
    private long bytesCompletados;
    private long descargadosActual;
    private long totalesActual;
    private int pistasTerminadas;
    private double maximaAlcanzada = -1;
    private DownloadPhase fase = DownloadPhase.PREPARANDO;
    private double velocidad;
    private Long eta;

    public ProgressAggregator(long totalEstimado) {
        this(totalEstimado, 1);
    }

    public ProgressAggregator(long totalEstimado, int pistasPrevistas) {
        this.totalEstimado = Math.max(0, totalEstimado);
        this.pistasPrevistas = Math.max(1, pistasPrevistas);
    }

    /** Incorpora una línea de progreso de descarga. */
    public ProgressSnapshot aplicar(ProgressParser.Descarga descarga) {
        if (descarga.formatId() != null && !descarga.formatId().equals(formatoActual)) {
            // Ha empezado otra pista: lo descargado de la anterior pasa al acumulado.
            if (formatoActual != null) {
                terminarPista();
            }
            formatoActual = descarga.formatId();
        }

        descargadosActual = Math.max(descargadosActual, descarga.descargados());
        totalesActual = Math.max(totalesActual, descarga.totales());
        fase = descarga.fase();
        velocidad = descarga.velocidad();
        eta = descarga.eta();

        if (descarga.terminado()) {
            terminarPista();
            formatoActual = null;
            velocidad = 0;
            eta = null;
        }

        return instantanea();
    }

    /** Incorpora un evento de posproceso: el progreso pasa a ser indeterminado. */
    public ProgressSnapshot aplicar(ProgressParser.Posproceso posproceso) {
        fase = posproceso.fase();
        velocidad = 0;
        eta = null;
        return new ProgressSnapshot(fase, fraccion(), descargadosGlobales(), totalGlobal(), 0, null);
    }

    /** Estado final: todo completado. */
    public ProgressSnapshot terminada() {
        fase = DownloadPhase.TERMINADA;
        maximaAlcanzada = 1;
        return new ProgressSnapshot(fase, 1, descargadosGlobales(), totalGlobal(), 0, null);
    }

    private void terminarPista() {
        bytesCompletados += Math.max(totalesActual, descargadosActual);
        descargadosActual = 0;
        totalesActual = 0;
        pistasTerminadas++;
    }

    private ProgressSnapshot instantanea() {
        return new ProgressSnapshot(fase, fraccion(), descargadosGlobales(), totalGlobal(),
                velocidad, etaGlobal());
    }

    private long descargadosGlobales() {
        return bytesCompletados + descargadosActual;
    }

    private long totalGlobal() {
        long porPistas = bytesCompletados + totalesActual;
        return Math.max(totalEstimado, porPistas);
    }

    /**
     * Avance global, entre 0 y 1, o negativo si no se puede saber.
     *
     * Nunca retrocede: una estimación corta o una pista que resulta más grande de lo que decía
     * harían bajar el número, y una barra que vuelve atrás parece que algo ha fallado.
     */
    private double fraccion() {
        double valor = calcular();
        if (valor < 0) {
            return maximaAlcanzada >= 0 ? maximaAlcanzada : -1;
        }
        maximaAlcanzada = Math.max(maximaAlcanzada, valor);
        return maximaAlcanzada;
    }

    private double calcular() {
        if (totalEstimado > 0) {
            return Math.min(1, (double) descargadosGlobales() / totalGlobal());
        }
        if (pistasPrevistas > 1) {
            double enCurso = totalesActual > 0 ? (double) descargadosActual / totalesActual : 0;
            return Math.min(1, (pistasTerminadas + enCurso) / pistasPrevistas);
        }
        return totalesActual > 0 ? (double) descargadosActual / totalesActual : -1;
    }

    /**
     * Tiempo restante de la descarga completa. yt-dlp solo conoce el de la pista actual, así
     * que si se sabe el total se recalcula con la velocidad instantánea.
     */
    private Long etaGlobal() {
        long total = totalGlobal();
        if (total > 0 && velocidad > 0) {
            long pendientes = total - descargadosGlobales();
            if (pendientes <= 0) {
                return 0L;
            }
            return (long) (pendientes / velocidad);
        }
        return eta;
    }
}
