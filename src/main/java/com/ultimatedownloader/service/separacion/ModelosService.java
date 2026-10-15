package com.ultimatedownloader.service.separacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.DownloadPhase;
import com.ultimatedownloader.model.separacion.Arquitectura;
import com.ultimatedownloader.model.separacion.ModeloUvr;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.ProcessRunner;
import com.ultimatedownloader.service.consola.Consola;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catálogo de modelos. La lista la da el propio audio-separator, así que siempre está al día.
 * Se pide una vez y se guarda en memoria: son 169 y arrancar Python tarda unos segundos.
 */
public final class ModelosService {

    private static final Logger log = LoggerFactory.getLogger(ModelosService.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Duration LIMITE = Duration.ofMinutes(3);

    private final EntornoPython entorno;

    private List<ModeloUvr> cache;

    public ModelosService(EntornoPython entorno) {
        this.entorno = entorno;
    }

    /** Lista completa, pidiéndosela a la herramienta la primera vez. */
    public List<ModeloUvr> modelos() throws AppException {
        if (cache == null) {
            cache = consultar();
        }
        return cache;
    }

    /** Olvida lo guardado; se usa tras descargar o borrar un modelo. */
    public void olvidar() {
        cache = null;
    }

    /** Modelo por su nombre de fichero, que es la clave real. */
    public Optional<ModeloUvr> porFichero(String fichero) throws AppException {
        return modelos().stream().filter(m -> m.fichero().equals(fichero)).findFirst();
    }

    /**
     * Si el modelo ya está descargado. Se mira la carpeta en vez de preguntárselo a la
     * herramienta: es instantáneo y no hace falta arrancar Python para pintar una lista.
     */
    public boolean descargado(ModeloUvr modelo) {
        Path carpeta = entorno.carpetaModelos();
        return Files.isRegularFile(carpeta.resolve(modelo.fichero()));
    }

    /** Lo que ocupa en disco un modelo ya descargado, o 0. */
    public long tamanoEnDisco(ModeloUvr modelo) {
        Path archivo = entorno.carpetaModelos().resolve(modelo.fichero());
        try {
            return Files.isRegularFile(archivo) ? Files.size(archivo) : 0;
        } catch (IOException e) {
            return 0;
        }
    }

    /** Borra el fichero de un modelo descargado. */
    public void borrar(ModeloUvr modelo) throws AppException {
        try {
            Files.deleteIfExists(entorno.carpetaModelos().resolve(modelo.fichero()));
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("modelos.no_se_ha_podido_borrar"),
                    Textos.t("modelos.puede_estar_en_uso"),
                    e.toString()), e);
        }
    }

    /**
     * Descarga un modelo sin separar nada. Lo baja la propia herramienta, que sabe de dónde sale
     * cada uno y lo deja con el nombre que luego espera.
     */
    public void descargar(ModeloUvr modelo, Consola consola) throws AppException {
        descargar(modelo, consola, null);
    }

    /**
     * Descarga un modelo informando del avance. El porcentaje sale de la barra que la propia
     * herramienta escribe mientras baja los ficheros.
     */
    public void descargar(ModeloUvr modelo, Consola consola, DoubleConsumer progreso)
            throws AppException {
        List<String> comando = List.of(entorno.audioSeparator().toString(),
                "--model_file_dir", entorno.carpetaModelos().toString(),
                "-m", modelo.fichero(),
                "--download_model_only");
        log.info("Descargando el modelo {}", modelo.fichero());
        try {
            SeparacionProgressParser avance = new SeparacionProgressParser();
            Consumer<String> porLinea = linea -> {
                registrar(consola, linea);
                if (progreso != null) {
                    avance.procesar(linea)
                            .filter(i -> i.fase() == DownloadPhase.DESCARGANDO_MODELO)
                            .ifPresent(i -> progreso.accept(i.fraccion()));
                }
            };
            ProcessRunner.ProcesoEnCurso proceso = ProcessRunner.lanzar(comando,
                    entorno.variables(), porLinea, porLinea);
            int codigo = proceso.esperar();
            if (codigo != 0) {
                throw new AppException(new ErrorAmistoso(
                        Textos.t("modelos.no_se_ha_podido_descargar", modelo.nombreCorto()),
                        Textos.t("modelos.comprueba_conexion_consola"),
                        Textos.t("separacion.codigo_del_motor", codigo)));
            }
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("modelos.no_se_ha_podido_arrancar"),
                    Textos.t("separacion.vuelve_a_instalarlo"), e.toString()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(new ErrorAmistoso(Textos.t("modelos.descarga_cancelada"), null), e);
        }
    }

    private static void registrar(Consola consola, String linea) {
        if (consola != null && linea != null && !linea.isBlank()) {
            consola.registrar(EntornoPython.TAREA, linea);
        }
    }

    /** Orden que lo use todo: primero los descargados, y dentro de cada grupo por SDR. */
    public Comparator<ModeloUvr> ordenHabitual() {
        return Comparator.comparing((ModeloUvr m) -> !descargado(m))
                .thenComparing(Comparator.comparingDouble(ModeloUvr::sdr).reversed())
                .thenComparing(ModeloUvr::nombre);
    }

    // ------------------------------------------------------------------ interno

    private List<ModeloUvr> consultar() throws AppException {
        if (!entorno.instalado()) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("separacion.motor_no_instalado"),
                    Textos.t("separacion.instalalo"), null));
        }
        List<String> comando = List.of(entorno.audioSeparator().toString(),
                "--list_models", "--list_format", "json",
                "--model_file_dir", entorno.carpetaModelos().toString());
        try {
            ProcessRunner.Resultado resultado =
                    ProcessRunner.ejecutar(comando, entorno.variables(), LIMITE);
            if (!resultado.correcto()) {
                throw new AppException(new ErrorAmistoso(
                        Textos.t("modelos.no_se_ha_podido_leer"),
                        Textos.t("modelos.mira_la_consola"),
                        resultado.error()));
            }
            return analizar(resultado.salida());
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("modelos.no_se_ha_podido_consultar"),
                    Textos.t("modelos.comprueba_motor"),
                    e.toString()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(new ErrorAmistoso(Textos.t("modelos.consulta_cancelada"), null), e);
        }
    }

    /**
     * Interpreta el JSON de --list_models. Viene un objeto por arquitectura y dentro uno por
     * modelo. De las puntuaciones solo se usa el SDR, que es la que se compara habitualmente.
     */
    public static List<ModeloUvr> analizar(String json) throws AppException {
        try {
            JsonNode raiz = JSON.readTree(json);
            List<ModeloUvr> modelos = new ArrayList<>();
            for (Map.Entry<String, JsonNode> entrada : raiz.properties()) {
                Arquitectura arquitectura = Arquitectura.desdeClave(entrada.getKey());
                for (Map.Entry<String, JsonNode> modelo : entrada.getValue().properties()) {
                    modelos.add(leerModelo(modelo.getKey(), modelo.getValue(), arquitectura));
                }
            }
            return List.copyOf(modelos);
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("modelos.lista_no_entendida"),
                    Textos.t("modelos.cambio_de_formato"),
                    e.toString()), e);
        }
    }

    private static ModeloUvr leerModelo(String nombre, JsonNode nodo, Arquitectura arquitectura) {
        List<String> pistas = textos(nodo.path("stems"));
        List<String> archivos = textos(nodo.path("download_files"));
        JsonNode puntuaciones = nodo.path("scores");
        String principal = nodo.path("target_stem").isMissingNode()
                || nodo.path("target_stem").isNull()
                ? null : nodo.path("target_stem").asText();
        return new ModeloUvr(nombre,
                nodo.path("filename").asText(nombre),
                arquitectura,
                pistas,
                principal,
                sdr(puntuaciones, "vocals"),
                sdr(puntuaciones, "instrumental"),
                archivos);
    }

    /** SDR de una pista; 0 cuando el modelo no tiene medida esa. */
    private static double sdr(JsonNode puntuaciones, String pista) {
        JsonNode nodo = puntuaciones.path(pista).path("SDR");
        return nodo.isNumber() ? nodo.asDouble() : 0;
    }

    /** download_files a veces trae nombres y a veces URL; aquí solo interesa cuántos son. */
    private static List<String> textos(JsonNode nodo) {
        if (!nodo.isArray()) {
            return List.of();
        }
        List<String> valores = new ArrayList<>();
        nodo.forEach(elemento -> valores.add(elemento.asText()));
        return valores;
    }

    /** Pistas distintas que aparecen en todo el catálogo, para los filtros. */
    public static List<String> pistasDisponibles(List<ModeloUvr> modelos) {
        return modelos.stream()
                .flatMap(m -> m.pistas().stream())
                .distinct()
                .sorted()
                .toList();
    }
}
