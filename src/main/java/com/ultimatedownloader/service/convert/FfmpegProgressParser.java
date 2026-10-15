package com.ultimatedownloader.service.convert;

import com.ultimatedownloader.model.DownloadPhase;
import com.ultimatedownloader.model.ProgressSnapshot;
import java.time.Duration;
import java.util.Optional;

/**
 * Traduce la salida de «ffmpeg -progress pipe:1» a progreso de la tarea: out_time_us dice
 * cuánto lleva convertido, speed a cuántas veces el tiempo real y progress cuándo termina.
 *
 * Tiene estado: cada conversión usa el suyo y las líneas le llegan desde un solo hilo.
 */
public final class FfmpegProgressParser {

    private final long duracionTotalUs;

    private long tiempoUs;
    private double velocidad;
    private long bytes;
    private boolean terminado;

    /** Con duración cero el progreso se queda indeterminado y solo se informa de la velocidad. */
    public FfmpegProgressParser(Duration duracionSalida) {
        this.duracionTotalUs = duracionSalida != null ? duracionSalida.toMillis() * 1000 : 0;
    }

    /**
     * Incorpora una línea.
     *
     * @return el progreso actualizado, o vacío si la línea no aporta nada nuevo
     */
    public Optional<ProgressSnapshot> procesar(String linea) {
        if (linea == null) {
            return Optional.empty();
        }
        int igual = linea.indexOf('=');
        if (igual <= 0) {
            return Optional.empty();
        }
        String clave = linea.substring(0, igual).strip();
        String valor = linea.substring(igual + 1).strip();

        switch (clave) {
            case "out_time_us", "out_time_ms" -> {
                // Pese al nombre, out_time_ms también viene en microsegundos.
                long leido = aLargo(valor);
                if (leido > 0) {
                    tiempoUs = leido;
                }
            }
            case "total_size" -> {
                long leido = aLargo(valor);
                if (leido > 0) {
                    bytes = leido;
                }
            }
            case "speed" -> velocidad = aVelocidad(valor);
            case "progress" -> {
                terminado = "end".equals(valor);
                // «progress» cierra cada bloque: es el momento de informar.
                return Optional.of(instantanea());
            }
            default -> {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Progreso actual, sin esperar al siguiente bloque. */
    public ProgressSnapshot instantanea() {
        double fraccion = duracionTotalUs > 0
                ? Math.min(1, (double) tiempoUs / duracionTotalUs)
                : -1;
        if (terminado) {
            fraccion = 1;
        }
        Long restante = null;
        if (duracionTotalUs > 0 && velocidad > 0 && !terminado) {
            long pendienteUs = Math.max(0, duracionTotalUs - tiempoUs);
            restante = (long) (pendienteUs / 1_000_000.0 / velocidad);
        }
        return new ProgressSnapshot(
                terminado ? DownloadPhase.TERMINADA : DownloadPhase.CONVIRTIENDO,
                fraccion, bytes, 0, 0, restante);
    }

    /** Cuántas veces más rápido que el tiempo real va la conversión. */
    public double velocidad() {
        return velocidad;
    }

    public boolean terminado() {
        return terminado;
    }

    /** ffmpeg escribe la velocidad como 12.3x, o N/A si todavía no la sabe. */
    static double aVelocidad(String valor) {
        if (valor == null || valor.isBlank() || valor.startsWith("N/A")) {
            return 0;
        }
        String limpio = valor.endsWith("x") ? valor.substring(0, valor.length() - 1) : valor;
        try {
            return Double.parseDouble(limpio.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long aLargo(String valor) {
        if (valor == null || valor.isBlank() || valor.startsWith("N/A")) {
            return 0;
        }
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
