package com.ultimatedownloader.service.separacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ultimatedownloader.model.ErrorAmistoso;
import com.ultimatedownloader.model.separacion.Acelerador;
import com.ultimatedownloader.model.separacion.EstadoEntorno;
import com.ultimatedownloader.service.AppException;
import com.ultimatedownloader.service.AppPaths;
import com.ultimatedownloader.service.Plataforma;
import com.ultimatedownloader.service.ProcessRunner;
import com.ultimatedownloader.service.consola.Consola;
import com.ultimatedownloader.service.consola.NivelConsola;
import com.ultimatedownloader.util.Textos;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entorno de Python propio de la aplicación: el gestor uv, el intérprete que él descarga, el
 * entorno virtual y los paquetes. No se toca ningún Python del sistema ni el PATH; borrando
 * esa carpeta el equipo queda como estaba.
 *
 * Se usa uv y no pip porque resuelve e instala en paralelo (PyTorch con CUDA son unos 3 GB) y
 * porque sabe descargarse un intérprete, que en un Windows sin Python es justo lo que falta.
 */
public final class EntornoPython {

    private static final Logger log = LoggerFactory.getLogger(EntornoPython.class);

    /** Tarea con la que se etiquetan en la consola las líneas de la instalación. */
    public static final String TAREA = "entorno";

    /** Versión fija: así una actualización de uv no cambia el comportamiento sin avisar. */
    public static final String VERSION_UV = "0.13.0";

    /** Paquete de uv para este sistema y este procesador. */
    public static final String URL_UV = urlUv(Plataforma.actual(), Plataforma.esArm());

    /** El nombre del paquete de uv lo forman el sistema y la arquitectura. */
    public static String urlUv(Plataforma sistema, boolean arm) {
        String arquitectura = arm ? "aarch64" : "x86_64";
        String paquete = switch (sistema) {
            case WINDOWS -> arquitectura + "-pc-windows-msvc.zip";
            case MAC -> arquitectura + "-apple-darwin.tar.gz";
            case LINUX -> arquitectura + "-unknown-linux-gnu.tar.gz";
        };
        return "https://github.com/astral-sh/uv/releases/download/" + VERSION_UV + "/uv-" + paquete;
    }

    /** Versión de Python que se instala; audio-separator pide 3.10 o superior. */
    public static final String VERSION_PYTHON = "3.12";

    /**
     * Índice de ruedas de PyTorch para CUDA 13. Tiene que coincidir con onnxruntime-gpu: con torch
     * en cu128 los modelos MDX y VR no encuentran cublas64_13.dll y caen a procesador sin avisar.
     * CUDA 13 además trae sm_120, que es lo que piden las Blackwell (RTX 50).
     */
    public static final String INDICE_CUDA = "https://download.pytorch.org/whl/cu130";

    /**
     * Paquetes de PyTorch que tienen que venir del mismo sitio y de la misma versión. torchvision
     * entra como dependencia de onnx2torch; si se queda la rueda de PyPI mientras torch viene del
     * índice de CUDA, al cargar un modelo ONNX salta «operator torchvision::nms does not exist».
     */
    private static final List<String> PAQUETES_TORCH =
            List.of("torch", "torchvision", "torchaudio");

    private static final String RECURSO_COMPROBAR = "/com/ultimatedownloader/python/comprobar.py";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path carpeta;
    private final Consola consola;
    private final AtomicReference<ProcessRunner.ProcesoEnCurso> enMarcha = new AtomicReference<>();

    /** Resultado de la comprobación de arranque; se calcula una vez y se guarda. */
    private volatile Boolean arranca;

    private final BarraPorPasos barra = new BarraPorPasos();

    public EntornoPython(Consola consola) {
        this(AppPaths.carpetaDatos().resolve("python"), consola);
    }

    public EntornoPython(Path carpeta, Consola consola) {
        this.carpeta = carpeta;
        this.consola = consola;
    }

    // ------------------------------------------------------------------ rutas

    public Path carpeta() {
        return carpeta;
    }

    public Path uv() {
        return carpeta.resolve(Plataforma.ejecutable("uv"));
    }

    /** Entorno virtual con audio-separator y sus dependencias. */
    public Path entorno() {
        return carpeta.resolve("entorno");
    }

    public Path python() {
        return entorno().resolve(Plataforma.carpetaBinariosPython())
                .resolve(Plataforma.ejecutable("python"));
    }

    public Path audioSeparator() {
        return entorno().resolve(Plataforma.carpetaBinariosPython())
                .resolve(Plataforma.ejecutable("audio-separator"));
    }

    /** Carpeta donde audio-separator guarda los modelos descargados. */
    public Path carpetaModelos() {
        return carpeta.resolve("modelos");
    }

    /**
     * Si el entorno está montado y se puede separar ya.
     *
     * No basta con que los archivos estén: un entorno virtual guarda rutas absolutas dentro, así
     * que si la carpeta ha cambiado de sitio ya no arranca. Por eso se lanza el intérprete de
     * verdad, una sola vez por sesión.
     */
    public boolean instalado() {
        if (!Files.isRegularFile(audioSeparator()) || !Files.isRegularFile(python())) {
            return false;
        }
        Boolean recordado = arranca;
        if (recordado == null) {
            recordado = compruebaQueArranca();
            arranca = recordado;
        }
        return recordado;
    }

    /**
     * Los archivos están pero no funciona. Se usa para explicarlo en la interfaz: no es lo mismo
     * «nunca lo has instalado» que «lo tenías y ha dejado de arrancar».
     */
    public boolean roto() {
        return Files.isRegularFile(audioSeparator()) && Files.isRegularFile(python())
                && !instalado();
    }

    private boolean compruebaQueArranca() {
        try {
            ProcessRunner.Resultado resultado = ProcessRunner.ejecutar(
                    List.of(python().toString(), "-c", "pass"), variables(),
                    Duration.ofSeconds(30));
            if (!resultado.correcto()) {
                log.warn("El entorno de Python no arranca: {}", resultado.error().strip());
            }
            return resultado.correcto();
        } catch (IOException e) {
            log.warn("El entorno de Python no arranca: {}", e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ------------------------------------------------------------------ estado

    /**
     * Pregunta al entorno qué tiene instalado y con qué va a calcular. Se ejecuta un guion dentro
     * del propio entorno porque torch.cuda.is_available() es lo que decidirá el motor de verdad.
     */
    public EstadoEntorno comprobar() {
        if (!instalado()) {
            return EstadoEntorno.sinInstalar();
        }
        return preguntarAlEntorno()
                .map(this::interpretar)
                .orElseGet(() -> new EstadoEntorno(true, audioSeparator(), null, Acelerador.CPU,
                        null, Textos.t("entorno.no_se_ha_podido_comprobar")));
    }

    /** Lanza el guion de comprobación y devuelve su JSON, si ha respondido. */
    private Optional<String> preguntarAlEntorno() {
        if (!Files.isRegularFile(python())) {
            return Optional.empty();
        }
        try {
            Path guion = extraerGuion();
            ProcessRunner.Resultado resultado = ProcessRunner.ejecutar(
                    List.of(python().toString(), guion.toString()),
                    variables(), Duration.ofMinutes(2));
            Optional<String> linea = resultado.salida().lines()
                    .filter(l -> l.startsWith("ENTORNO="))
                    .findFirst();
            if (linea.isEmpty()) {
                log.warn("El guion de comprobación no ha respondido: {}", resultado.error());
            }
            return linea.map(l -> l.substring("ENTORNO=".length()));
        } catch (IOException e) {
            log.warn("No se ha podido comprobar el entorno", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Comprueba que la rueda de PyTorch recién instalada habla de verdad con la tarjeta.
     *
     * El índice de CUDA sirve también ruedas «+cpu» cuando no hay una para esa combinación, y
     * instalarla no da ningún error: se descubriría media hora después, calculando con el
     * procesador sin que nadie lo haya pedido.
     */
    private void comprobarTorch() {
        Optional<String> json = preguntarAlEntorno();
        if (json.isEmpty()) {
            return;
        }
        try {
            JsonNode raiz = JSON.readTree(json.get());
            String version = texto(raiz, "torch");
            if (raiz.path("cuda").asBoolean()) {
                registrar(Textos.t("entorno.pytorch_ve_la_tarjeta", version));
            } else if (consola != null) {
                consola.registrar(TAREA, NivelConsola.AVISO,
                        Textos.t("entorno.pytorch_no_habla", version));
            }
        } catch (IOException e) {
            log.debug("Respuesta ilegible al comprobar torch: {}", json.get());
        }
    }

    /** Traduce la respuesta del guion al registro que usa la interfaz. */
    EstadoEntorno interpretar(String json) {
        try {
            JsonNode raiz = JSON.readTree(json);
            String version = texto(raiz, "separador");
            String dispositivo = texto(raiz, "dispositivo");
            String motivo = texto(raiz, "motivo");
            Acelerador acelerador = Acelerador.CPU;
            if (raiz.path("cuda").asBoolean()) {
                acelerador = Acelerador.CUDA;
            } else if (raiz.path("directml").asBoolean()) {
                acelerador = Acelerador.DIRECTML;
            }
            String aviso = acelerador == Acelerador.CPU
                    ? Textos.t("entorno.aviso_procesador")
                            + (motivo != null ? " (" + motivo + ")" : "")
                    : null;
            return new EstadoEntorno(true, audioSeparator(), version, acelerador,
                    dispositivo, aviso);
        } catch (IOException e) {
            log.warn("Respuesta ilegible del guion de comprobación: {}", json);
            return new EstadoEntorno(true, audioSeparator(), null, Acelerador.CPU, null,
                    Textos.t("entorno.no_se_ha_podido_comprobar"));
        }
    }

    // ------------------------------------------------------------------ instalación

    /** Avance de la instalación: fase visible, detalle en vivo y progreso global. */
    @FunctionalInterface
    public interface Avance {

        void avanzar(String fase, String detalle, double progreso);
    }

    /**
     * Monta el entorno entero. Es largo, descarga varios GB. Se puede cancelar en cualquier
     * momento y lo que quede a medias se retoma, porque cada paso mira antes si ya está hecho.
     */
    public void instalar(Acelerador acelerador, Avance avance, BooleanSupplier cancelado)
            throws AppException {
        arranca = null;
        try {
            Files.createDirectories(carpeta);
            Files.createDirectories(carpetaModelos());

            paso(avance, Textos.t("entorno.paso_uv"), 0.02, 0.10);
            descargarUv(avance, cancelado);
            comprobarCancelacion(cancelado);

            paso(avance, Textos.t("entorno.paso_python", VERSION_PYTHON), 0.10, 0.20);
            uv(List.of("python", "install", VERSION_PYTHON), avance,
                    Textos.t("entorno.paso_python", VERSION_PYTHON), cancelado);

            paso(avance, Textos.t("entorno.paso_entorno"), 0.20, 0.25);
            uv(List.of("venv", "--python", VERSION_PYTHON,
                            "--python-preference", "only-managed",
                            entorno().toString()),
                    avance, Textos.t("entorno.paso_entorno"), cancelado);

            paso(avance, Textos.t("entorno.paso_separador"), 0.25,
                    acelerador == Acelerador.CUDA ? 0.55 : 0.92);
            // audioread va aparte: audio-separator lo importa, pero las versiones nuevas de
            // librosa ya no lo arrastran y el motor falla al arrancar sin él.
            uv(List.of("pip", "install", "--python", python().toString(),
                            "audio-separator[" + acelerador.extraPip() + "]", "audioread"),
                    avance, Textos.t("entorno.paso_separador"), cancelado);

            if (acelerador == Acelerador.CUDA) {
                paso(avance, Textos.t("entorno.paso_torch_largo"), 0.55, 0.92);
                uv(argumentosTorch(), avance, Textos.t("entorno.paso_torch"), cancelado);
                comprobarTorch();
            }

            paso(avance, Textos.t("entorno.paso_cache_largo"), 0.92, 0.96);
            uv(List.of("cache", "clean"), avance, Textos.t("entorno.paso_cache"), cancelado);

            paso(avance, Textos.t("entorno.paso_dispositivo"), 0.96, 1);
            EstadoEntorno estado = comprobar();
            registrar(Textos.t("entorno.listo_con_dispositivo", estado.resumenDispositivo()));
            paso(avance, Textos.t("entorno.paso_listo"), 1, 1);
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("entorno.no_se_ha_podido_preparar"),
                    Textos.t("entorno.comprueba_sitio"),
                    e.toString()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(new ErrorAmistoso(Textos.t("entorno.instalacion_cancelada"), null), e);
        }
    }

    /** Mata el proceso que esté en marcha; lo llama el botón de cancelar. */
    public void cancelar() {
        ProcessRunner.ProcesoEnCurso proceso = enMarcha.get();
        if (proceso != null) {
            proceso.cancelar();
        }
    }

    /** Borra el entorno. Los modelos se conservan: ocupan mucho y no dependen de él. */
    public void desinstalar() throws AppException {
        arranca = null;
        borrarArbol(entorno());
        borrarArbol(carpeta.resolve("runtimes"));
        borrarArbol(carpeta.resolve("cache"));
    }

    /** Espacio que ocupa ahora mismo todo lo instalado, en bytes. */
    public long tamanoEnDisco() {
        return tamanoDe(carpeta);
    }

    // ------------------------------------------------------------------ pasos

    /**
     * Sustituye las ruedas de PyPI por las del índice de CUDA. Va después de audio-separator,
     * cuyas dependencias traen torch para procesador; sin --reinstall-package uv las daría por
     * buenas y no cambiaría nada.
     */
    private List<String> argumentosTorch() {
        List<String> argumentos = new ArrayList<>(
                List.of("pip", "install", "--python", python().toString()));
        for (String paquete : PAQUETES_TORCH) {
            argumentos.add("--reinstall-package");
            argumentos.add(paquete);
        }
        argumentos.addAll(PAQUETES_TORCH);
        argumentos.add("--index-url");
        argumentos.add(INDICE_CUDA);
        return argumentos;
    }

    private void descargarUv(Avance avance, BooleanSupplier cancelado)
            throws AppException, IOException {
        if (Files.isRegularFile(uv())) {
            registrar(Textos.t("entorno.uv_ya_estaba"));
            return;
        }
        Path paquete = carpeta.resolve(Plataforma.esWindows() ? "uv.zip" : "uv.tar.gz");
        try {
            descargar(URL_UV, paquete, avance, cancelado);
            if (Plataforma.esWindows()) {
                extraerUv(paquete);
            } else {
                extraerUvDelTar(paquete);
            }
            registrar(Textos.t("entorno.uv_listo", VERSION_UV));
        } finally {
            Files.deleteIfExists(paquete);
        }
    }

    /**
     * Fuera de Windows uv viene en un .tar.gz; se usa el «tar» del sistema, que está siempre.
     * El paquete trae los dos programas dentro de una carpeta, de ahí el --strip-components.
     */
    private void extraerUvDelTar(Path paquete) throws AppException, IOException {
        try {
            ProcessRunner.Resultado resultado = ProcessRunner.ejecutar(List.of(
                    "tar", "-xzf", paquete.toString(),
                    "-C", carpeta.toString(), "--strip-components=1"), Duration.ofMinutes(3));
            if (resultado.codigoSalida() != 0) {
                throw new AppException(new ErrorAmistoso(
                        Textos.t("entorno.no_se_ha_podido_descargar_uv"),
                        Textos.t("entorno.comprueba_conexion"), resultado.error()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(new ErrorAmistoso(
                    Textos.t("entorno.instalacion_cancelada"), null), e);
        }
        for (String nombre : List.of("uv", "uvx")) {
            Path programa = carpeta.resolve(nombre);
            if (Files.isRegularFile(programa) && !programa.toFile().setExecutable(true, false)) {
                log.warn("No se ha podido marcar {} como ejecutable", programa);
            }
        }
    }

    private void extraerUv(Path zip) throws IOException {
        try (ZipInputStream entrada = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry elemento;
            while ((elemento = entrada.getNextEntry()) != null) {
                String nombre = Path.of(elemento.getName()).getFileName().toString()
                        .toLowerCase(Locale.ROOT);
                if (!nombre.equals("uv.exe") && !nombre.equals("uvx.exe")) {
                    continue;
                }
                // Solo se escriben nombres fijos nuestros, nunca la ruta que venga en el ZIP.
                Files.copy(entrada, carpeta.resolve(nombre), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Ejecuta uv con las variables que lo encierran en nuestra carpeta. El progreso va
     * indeterminado a propósito: uv no da un porcentaje global, y es mejor enseñar la línea real
     * de lo que está bajando que inventarse una barra.
     */
    private void uv(List<String> argumentos, Avance avance, String fase, BooleanSupplier cancelado)
            throws AppException, IOException, InterruptedException {
        comprobarCancelacion(cancelado);
        List<String> comando = new ArrayList<>();
        comando.add(uv().toString());
        comando.addAll(argumentos);

        ProcessRunner.ProcesoEnCurso proceso = ProcessRunner.lanzar(comando, variables(),
                linea -> salida(linea, avance, fase, NivelConsola.INFO),
                linea -> salida(linea, avance, fase, NivelConsola.INFO));
        enMarcha.set(proceso);
        try {
            int codigo = proceso.esperar();
            if (proceso.cancelado()) {
                throw new InterruptedException("Instalación cancelada");
            }
            if (codigo != 0) {
                throw new AppException(new ErrorAmistoso(
                        Textos.t("entorno.paso_fallido", fase.toLowerCase(Locale.ROOT)),
                        Textos.t("entorno.mira_la_consola"),
                        "uv ha terminado con el código " + codigo));
            }
        } finally {
            enMarcha.set(null);
        }
    }

    /** Las líneas de uv van íntegras a la consola y, resumidas, al asistente. */
    private void salida(String linea, Avance avance, String fase, NivelConsola nivel) {
        if (linea == null || linea.isBlank()) {
            return;
        }
        if (consola != null) {
            consola.registrar(TAREA, nivel, linea);
        }
        if (avance != null && esInteresante(linea)) {
            avance.avanzar(fase, linea.trim(), barra.avanzar());
        }
    }

    /** Filtra el ruido: solo se enseña arriba lo que dice qué está pasando ahora. */
    static boolean esInteresante(String linea) {
        String l = linea.trim();
        return l.startsWith("Downloading") || l.startsWith("Resolved")
                || l.startsWith("Prepared") || l.startsWith("Installed")
                || l.startsWith("Installing") || l.startsWith("Using")
                || l.startsWith("Creating") || l.startsWith("Removed");
    }

    /** Empieza un paso de la instalación y le asigna su tramo de barra. */
    private void paso(Avance avance, String fase, double desde, double hasta) {
        registrar(fase);
        barra.empezar(desde, hasta);
        if (avance != null) {
            avance.avanzar(fase, null, desde);
        }
    }

    private void registrar(String texto) {
        log.info(texto);
        if (consola != null) {
            consola.registrar(TAREA, NivelConsola.INFO, texto);
        }
    }

    private static void comprobarCancelacion(BooleanSupplier cancelado)
            throws InterruptedException {
        if (cancelado != null && cancelado.getAsBoolean()) {
            throw new InterruptedException("Instalación cancelada");
        }
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * Variables que mantienen a uv dentro de nuestra carpeta. Sin ellas pondría los intérpretes y
     * la caché en la carpeta compartida de %LOCALAPPDATA%.
     */
    Map<String, String> variables() {
        return Map.of(
                "UV_PYTHON_INSTALL_DIR", carpeta.resolve("runtimes").toString(),
                "UV_CACHE_DIR", carpeta.resolve("cache").toString(),
                "UV_TOOL_DIR", carpeta.resolve("tools").toString(),
                "UV_NO_MODIFY_PATH", "1",
                "UV_NO_PROGRESS", "1",
                "VIRTUAL_ENV", entorno().toString());
    }

    /** Copia el guion de comprobación al disco; python necesita un fichero de verdad. */
    private Path extraerGuion() throws IOException {
        Path destino = carpeta.resolve("comprobar.py");
        try (InputStream entrada = EntornoPython.class.getResourceAsStream(RECURSO_COMPROBAR)) {
            if (entrada == null) {
                throw new IOException("Falta el recurso " + RECURSO_COMPROBAR);
            }
            Files.createDirectories(carpeta);
            Files.copy(entrada, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        return destino;
    }

    private void descargar(String url, Path destino, Avance avance, BooleanSupplier cancelado)
            throws AppException {
        try {
            HttpClient cliente = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "UltimateDownloader")
                    .GET()
                    .build();
            HttpResponse<InputStream> respuesta =
                    cliente.send(peticion, HttpResponse.BodyHandlers.ofInputStream());
            if (respuesta.statusCode() != 200) {
                throw new IOException("El servidor ha respondido " + respuesta.statusCode());
            }
            long total = respuesta.headers().firstValueAsLong("content-length").orElse(-1);
            try (InputStream entrada = respuesta.body();
                 OutputStream salida = Files.newOutputStream(destino)) {
                byte[] bufer = new byte[1 << 16];
                long copiados = 0;
                int ultimoPorciento = -1;
                int leidos;
                while ((leidos = entrada.read(bufer)) > 0) {
                    salida.write(bufer, 0, leidos);
                    copiados += leidos;
                    // Un aviso por cada punto porcentual: con uno por bloque de 64 kB se
                    // llenaría la consola de líneas idénticas.
                    int porciento = total > 0 ? (int) (100 * copiados / total) : -1;
                    if (avance != null && porciento > ultimoPorciento) {
                        ultimoPorciento = porciento;
                        avance.avanzar(Textos.t("entorno.paso_uv"), null,
                                0.02 + 0.08 * porciento / 100.0);
                    }
                    comprobarCancelacion(cancelado);
                }
            }
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("entorno.no_se_ha_podido_descargar_uv"),
                    Textos.t("entorno.comprueba_la_conexion"),
                    e.toString()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException(new ErrorAmistoso(Textos.t("entorno.instalacion_cancelada"), null), e);
        }
    }

    private static void borrarArbol(Path raiz) throws AppException {
        if (!Files.exists(raiz)) {
            return;
        }
        try (var rutas = Files.walk(raiz)) {
            for (Path ruta : rutas.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(ruta);
            }
        } catch (IOException e) {
            throw new AppException(new ErrorAmistoso(
                    Textos.t("entorno.no_se_ha_podido_borrar"),
                    Textos.t("entorno.cierra_separaciones"),
                    e.toString()), e);
        }
    }

    private static long tamanoDe(Path raiz) {
        if (!Files.exists(raiz)) {
            return 0;
        }
        try (var rutas = Files.walk(raiz)) {
            return rutas.filter(Files::isRegularFile).mapToLong(ruta -> {
                try {
                    return Files.size(ruta);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String texto(JsonNode raiz, String campo) {
        JsonNode nodo = raiz.path(campo);
        return nodo.isMissingNode() || nodo.isNull() ? null : nodo.asText();
    }
}
