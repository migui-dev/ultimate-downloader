package com.ultimatedownloader.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ultimatedownloader.model.Settings;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Carga y guarda los ajustes. Si el fichero no existe, está corrupto o es de otra versión, se
 * usan los valores por defecto: unos ajustes ilegibles no deben impedir abrir la aplicación.
 *
 * Al guardar se escribe un temporal y se mueve encima, para que un corte a mitad no deje los
 * ajustes partidos.
 */
public final class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final Path fichero;
    private final ObjectMapper mapper;
    private Settings ajustes;

    public SettingsService() {
        this(AppPaths.ficheroAjustes());
    }

    public SettingsService(Path fichero) {
        this.fichero = fichero;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.ajustes = cargar();
    }

    public Settings ajustes() {
        return ajustes;
    }

    public Path fichero() {
        return fichero;
    }

    /** Vuelve a leer el fichero del disco. */
    public void recargar() {
        ajustes = cargar();
    }

    private Settings cargar() {
        if (!Files.isRegularFile(fichero)) {
            log.info("No hay ajustes guardados; se usan los valores por defecto");
            return conValoresPorDefecto(new Settings());
        }
        try {
            Settings leidos = mapper.readValue(Files.readString(fichero), Settings.class);
            log.info("Ajustes cargados de {}", fichero);
            return conValoresPorDefecto(leidos);
        } catch (IOException | RuntimeException e) {
            log.warn("Los ajustes de {} no se han podido leer ({}); se usan los valores por defecto",
                    fichero, e.getMessage());
            return conValoresPorDefecto(new Settings());
        }
    }

    /** Rellena lo que falte: la primera vez, la carpeta de destino es la de Descargas. */
    private Settings conValoresPorDefecto(Settings s) {
        if (s.getCarpetaDestino() == null || s.getCarpetaDestino().isBlank()) {
            s.setCarpetaDestino(AppPaths.carpetaDescargasPorDefecto().toString());
        }
        if (s.getPlantillaNombre() == null || s.getPlantillaNombre().isBlank()) {
            s.setPlantillaNombre(FilenameTemplate.PLANTILLA_POR_DEFECTO);
        }
        return s;
    }

    /** Guarda los ajustes. No propaga errores: no vale la pena molestar al usuario por esto. */
    public void guardar() {
        Path temporal = fichero.resolveSibling(fichero.getFileName() + ".tmp");
        try {
            Files.createDirectories(fichero.getParent());
            mapper.writeValue(temporal.toFile(), ajustes);
            try {
                Files.move(temporal, fichero,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, fichero, StandardCopyOption.REPLACE_EXISTING);
            }
            log.debug("Ajustes guardados en {}", fichero);
        } catch (IOException e) {
            log.warn("No se han podido guardar los ajustes en {}: {}", fichero, e.getMessage());
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException ignorado) {
                // Da igual: el temporal se sobrescribirá en el próximo guardado.
            }
        }
    }
}
