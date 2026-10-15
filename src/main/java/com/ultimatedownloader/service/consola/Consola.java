package com.ultimatedownloader.service.consola;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Registro común de los procesos externos: aquí llega todo lo que escriben yt-dlp, ffmpeg y
 * audio-separator, más los mensajes de la aplicación.
 *
 * Es una lista observable acotada, para que una descarga larga no se coma la memoria. Las
 * líneas llegan desde los hilos lectores, así que la inserción se despacha al hilo de JavaFX.
 */
public final class Consola {

    /** Líneas que se conservan; a partir de ahí se descartan las más antiguas. */
    public static final int LIMITE = 5000;

    private final ObservableList<LineaConsola> lineas = FXCollections.observableArrayList();
    private final Consumer<Runnable> despachador;

    public Consola(Consumer<Runnable> despachador) {
        this.despachador = despachador;
    }

    public ObservableList<LineaConsola> lineas() {
        return lineas;
    }

    /** Registra una línea deduciendo el nivel de su texto. */
    public void registrar(String tarea, String texto) {
        registrar(tarea, NivelConsola.deTexto(texto), texto);
    }

    public void registrar(String tarea, NivelConsola nivel, String texto) {
        if (texto == null || texto.isBlank()) {
            return;
        }
        LineaConsola linea = LineaConsola.de(tarea, nivel, texto.stripTrailing());
        despachador.accept(() -> {
            lineas.add(linea);
            while (lineas.size() > LIMITE) {
                lineas.remove(0);
            }
        });
    }

    public void limpiar() {
        despachador.accept(lineas::clear);
    }

    /** Nombres de tarea presentes, para el filtro de la consola. */
    public List<String> tareas() {
        return lineas.stream().map(LineaConsola::tarea).distinct().sorted().toList();
    }

    /** Texto completo, para copiar o guardar. */
    public String comoTexto() {
        return lineas.stream().map(LineaConsola::comoTexto).collect(Collectors.joining("\n"));
    }

    public void guardarEn(Path destino) throws IOException {
        Files.writeString(destino, comoTexto(), StandardCharsets.UTF_8);
    }
}
