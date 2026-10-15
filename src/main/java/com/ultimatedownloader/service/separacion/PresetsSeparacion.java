package com.ultimatedownloader.service.separacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ultimatedownloader.model.calidad.FormatoSalida;
import com.ultimatedownloader.model.calidad.NivelCalidad;
import com.ultimatedownloader.model.separacion.OpcionesSeparacion;
import com.ultimatedownloader.model.separacion.SeparacionRequest;
import com.ultimatedownloader.service.AppPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Combinaciones de modelos y opciones guardadas con un nombre. Van en su propio fichero, no en
 * los ajustes: es una lista que puede crecer y que conviene poder copiar o borrar aparte.
 */
public final class PresetsSeparacion {

    private static final Logger log = LoggerFactory.getLogger(PresetsSeparacion.class);

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path fichero;
    private final List<Preset> presets = new ArrayList<>();

    public PresetsSeparacion() {
        this(AppPaths.carpetaDatos().resolve("presets-separacion.json"));
    }

    public PresetsSeparacion(Path fichero) {
        this.fichero = fichero;
        cargar();
    }

    /** Unos ajustes de separación con nombre. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Preset(String nombre, List<String> modelos,
                         SeparacionRequest.ModoVariosModelos modo, String algoritmo,
                         String pista, FormatoSalida formato, NivelCalidad nivel,
                         int frecuencia, OpcionesSeparacion opciones) {

        public Preset {
            modelos = modelos == null ? List.of() : List.copyOf(modelos);
        }

        @Override
        public String toString() {
            return nombre;
        }
    }

    public List<Preset> todos() {
        return List.copyOf(presets);
    }

    public Optional<Preset> porNombre(String nombre) {
        return presets.stream().filter(p -> p.nombre().equals(nombre)).findFirst();
    }

    /** Guarda unos ajustes; si ya había otros con ese nombre, los sustituye. */
    public void guardar(Preset preset) {
        presets.removeIf(p -> p.nombre().equalsIgnoreCase(preset.nombre()));
        presets.add(preset);
        presets.sort((a, b) -> a.nombre().compareToIgnoreCase(b.nombre()));
        escribir();
    }

    public void borrar(String nombre) {
        if (presets.removeIf(p -> p.nombre().equals(nombre))) {
            escribir();
        }
    }

    // ------------------------------------------------------------------ disco

    private void cargar() {
        if (!Files.isRegularFile(fichero)) {
            return;
        }
        try {
            Preset[] leidos = JSON.readValue(Files.readString(fichero), Preset[].class);
            presets.addAll(List.of(leidos));
        } catch (IOException e) {
            // Un fichero corrupto no puede impedir usar el módulo: se empieza de cero.
            log.warn("No se han podido leer los ajustes guardados de separación: {}",
                    e.getMessage());
        }
    }

    private void escribir() {
        try {
            Files.createDirectories(fichero.getParent());
            Files.writeString(fichero, JSON.writeValueAsString(presets));
        } catch (IOException e) {
            log.warn("No se han podido guardar los ajustes de separación", e);
        }
    }
}
