package com.ultimatedownloader.service.separacion;

import com.ultimatedownloader.model.separacion.Acelerador;
import com.ultimatedownloader.model.separacion.InfoGpu;
import com.ultimatedownloader.service.Plataforma;
import com.ultimatedownloader.service.ProcessRunner;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Averigua qué gráfica hay antes de instalar nada; sirve solo para elegir qué instalar. Lo que
 * se va a usar de verdad lo dice EntornoPython.comprobar() preguntándoselo a PyTorch.
 */
public final class DetectorGpu {

    private static final Logger log = LoggerFactory.getLogger(DetectorGpu.class);

    private static final Duration LIMITE = Duration.ofSeconds(15);

    private DetectorGpu() {
    }

    /**
     * Tarjeta NVIDIA, si nvidia-smi responde. Ese ejecutable lo pone el propio controlador y está
     * en el PATH, así que su ausencia ya dice que no hay NVIDIA usable.
     */
    public static Optional<InfoGpu> nvidia() {
        List<String> comando = List.of("nvidia-smi",
                "--query-gpu=name,driver_version,memory.total",
                "--format=csv,noheader,nounits");
        try {
            ProcessRunner.Resultado resultado = ProcessRunner.ejecutar(comando, LIMITE);
            if (!resultado.correcto()) {
                return Optional.empty();
            }
            return primeraLinea(resultado.salida());
        } catch (IOException e) {
            log.debug("nvidia-smi no está disponible: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** Lee la primera fila del CSV: NVIDIA GeForce RTX 5070, 617.14, 12227. */
    static Optional<InfoGpu> primeraLinea(String salida) {
        if (salida == null) {
            return Optional.empty();
        }
        for (String linea : salida.lines().toList()) {
            String[] campos = linea.split(",");
            if (campos.length < 3 || campos[0].isBlank()) {
                continue;
            }
            return Optional.of(new InfoGpu(campos[0].trim(), campos[1].trim(),
                    entero(campos[2])));
        }
        return Optional.empty();
    }

    /**
     * Qué conviene instalar aquí. Con NVIDIA, CUDA. Sin ella, en Windows queda DirectML, que
     * vale para AMD e Intel; en macOS el propio PyTorch usa la GPU de Apple sin instalar nada
     * aparte, y en Linux sin NVIDIA toca procesador.
     */
    public static Acelerador recomendado() {
        if (nvidia().isPresent()) {
            return Acelerador.CUDA;
        }
        if (Plataforma.esWindows() && hayGraficaDedicada()) {
            return Acelerador.DIRECTML;
        }
        return Acelerador.CPU;
    }

    /** Pregunta a Windows por los adaptadores de vídeo instalados. */
    private static boolean hayGraficaDedicada() {
        List<String> comando = List.of("powershell", "-NoProfile", "-NonInteractive", "-Command",
                "(Get-CimInstance Win32_VideoController).Name");
        try {
            ProcessRunner.Resultado resultado = ProcessRunner.ejecutar(comando, LIMITE);
            String salida = resultado.salida().toLowerCase(Locale.ROOT);
            return salida.contains("radeon") || salida.contains("amd")
                    || salida.contains("arc") || salida.contains("nvidia");
        } catch (IOException e) {
            log.debug("No se ha podido consultar los adaptadores de vídeo: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static int entero(String texto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
