package com.ultimatedownloader.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Único punto de la aplicación que lanza procesos externos. Siempre con una lista de
 * argumentos, nunca con una orden en una cadena: así no hay nada que escapar.
 *
 * Las dos salidas se leen en hilos aparte: si no se vacían, el proceso se bloquea al llenarse
 * el búfer de la tubería (el JSON de -J pasa de 100 kB).
 */
public final class ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);

    /** Hilos de lectura; son demonios para que no impidan cerrar la aplicación. */
    private static final ExecutorService LECTORES = Executors.newCachedThreadPool(tarea -> {
        Thread hilo = new Thread(tarea, "lector-proceso");
        hilo.setDaemon(true);
        return hilo;
    });

    private ProcessRunner() {
    }

    /** Lo que ha dejado un proceso que ya terminó. */
    public record Resultado(int codigoSalida, String salida, String error) {

        public boolean correcto() {
            return codigoSalida == 0;
        }
    }

    /** Ejecuta un proceso y espera a que termine. Con límite null espera lo que haga falta. */
    public static Resultado ejecutar(List<String> comando, Duration limite)
            throws IOException, InterruptedException {
        return ejecutar(comando, Map.of(), limite);
    }

    /**
     * Igual, pero añadiendo variables de entorno. Se suman a las del sistema, no las sustituyen:
     * uv necesita seguir viendo PATH y TEMP aunque le digamos dónde guardar sus cosas.
     */
    public static Resultado ejecutar(List<String> comando, Map<String, String> entorno,
                                     Duration limite) throws IOException, InterruptedException {
        log.debug("Ejecutando: {}", comando);
        Process proceso = conEntorno(comando, entorno).start();
        CompletableFuture<String> salida = leerTodo(proceso.getInputStream());
        CompletableFuture<String> error = leerTodo(proceso.getErrorStream());

        boolean terminado = limite == null
                ? esperarSinLimite(proceso)
                : proceso.waitFor(limite.toMillis(), TimeUnit.MILLISECONDS);

        if (!terminado) {
            destruir(proceso);
            throw new IOException("El proceso ha tardado más de " + limite.toSeconds() + " s");
        }
        try {
            return new Resultado(proceso.exitValue(), salida.get(), error.get());
        } catch (ExecutionException e) {
            throw new IOException("No se ha podido leer la salida del proceso", e.getCause());
        }
    }

    /**
     * Lanza un proceso y entrega sus líneas según aparecen. Los consumidores se llaman desde los
     * hilos de lectura, nunca desde el de JavaFX: quien toque la interfaz que lo despache él.
     */
    public static ProcesoEnCurso lanzar(List<String> comando,
                                        Consumer<String> porLinea,
                                        Consumer<String> porLineaError) throws IOException {
        return lanzar(comando, Map.of(), porLinea, porLineaError);
    }

    /** Igual que lanzar(List, Consumer, Consumer) con variables de entorno añadidas. */
    public static ProcesoEnCurso lanzar(List<String> comando, Map<String, String> entorno,
                                        Consumer<String> porLinea,
                                        Consumer<String> porLineaError) throws IOException {
        log.debug("Lanzando: {}", comando);
        Process proceso = conEntorno(comando, entorno).start();
        CompletableFuture<Void> lectorSalida = leerLineas(proceso.getInputStream(), porLinea);
        CompletableFuture<Void> lectorError = leerLineas(proceso.getErrorStream(), porLineaError);
        return new ProcesoEnCurso(proceso, lectorSalida, lectorError);
    }

    /** Proceso en marcha, con cancelación limpia. */
    public static final class ProcesoEnCurso {

        private final Process proceso;
        private final CompletableFuture<Void> lectorSalida;
        private final CompletableFuture<Void> lectorError;
        private final AtomicBoolean cancelado = new AtomicBoolean();

        private ProcesoEnCurso(Process proceso, CompletableFuture<Void> lectorSalida,
                               CompletableFuture<Void> lectorError) {
            this.proceso = proceso;
            this.lectorSalida = lectorSalida;
            this.lectorError = lectorError;
        }

        /** Espera a que termine y a que se haya leído toda la salida. */
        public int esperar() throws InterruptedException {
            int codigo = proceso.waitFor();
            lectorSalida.join();
            lectorError.join();
            return codigo;
        }

        /**
         * Mata el proceso y sus hijos (yt-dlp lanza ffmpeg). Primero pide el cierre ordenado y a los
         * tres segundos fuerza. Los descendientes se enumeran antes de matar al padre, porque después
         * ya no se pueden.
         */
        public void cancelar() {
            if (!cancelado.compareAndSet(false, true)) {
                return;
            }
            log.info("Cancelando el proceso {}", proceso.pid());
            destruir(proceso);
        }

        public boolean cancelado() {
            return cancelado.get();
        }

        public boolean vivo() {
            return proceso.isAlive();
        }

        public long pid() {
            return proceso.pid();
        }
    }

    // ------------------------------------------------------------------ interno

    private static ProcessBuilder conEntorno(List<String> comando, Map<String, String> entorno) {
        ProcessBuilder constructor = new ProcessBuilder(comando);
        if (entorno != null) {
            constructor.environment().putAll(entorno);
        }
        return constructor;
    }

    private static void destruir(Process proceso) {
        List<ProcessHandle> hijos = proceso.descendants().toList();
        hijos.forEach(ProcessHandle::destroy);
        proceso.destroy();
        try {
            if (!proceso.waitFor(3, TimeUnit.SECONDS)) {
                hijos.forEach(ProcessHandle::destroyForcibly);
                proceso.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proceso.destroyForcibly();
        }
    }

    private static boolean esperarSinLimite(Process proceso) throws InterruptedException {
        proceso.waitFor();
        return true;
    }

    private static CompletableFuture<String> leerTodo(InputStream flujo) {
        return CompletableFuture.supplyAsync(() -> {
            try (BufferedReader lector = nuevoLector(flujo)) {
                StringBuilder sb = new StringBuilder();
                String linea;
                while ((linea = lector.readLine()) != null) {
                    sb.append(linea).append('\n');
                }
                return sb.toString();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, LECTORES);
    }

    private static CompletableFuture<Void> leerLineas(InputStream flujo, Consumer<String> consumidor) {
        return CompletableFuture.runAsync(() -> {
            try (BufferedReader lector = nuevoLector(flujo)) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    if (consumidor != null) {
                        consumidor.accept(linea);
                    }
                }
            } catch (IOException e) {
                // Al matar el proceso la tubería se cierra de golpe: no es un error real.
                log.debug("Lectura interrumpida: {}", e.getMessage());
            }
        }, LECTORES);
    }

    /** yt-dlp se lanza con --encoding UTF-8, así que su salida se lee como UTF-8. */
    private static BufferedReader nuevoLector(InputStream flujo) {
        return new BufferedReader(new InputStreamReader(flujo, StandardCharsets.UTF_8));
    }
}
