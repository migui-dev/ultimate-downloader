package com.ultimatedownloader.service.separacion;

import com.ultimatedownloader.model.DownloadPhase;
import com.ultimatedownloader.model.ProgressSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traduce lo que escribe audio-separator a progreso de la tarea.
 *
 * La herramienta mezcla su registro con las barras de tqdm, que se reescriben con retorno de
 * carro; como readLine() corta también ahí, cada repintado llega como una línea suelta. Hay
 * dos barras y se distinguen por la unidad: la del modelo mide en bytes y la otra en pasadas.
 */
public final class SeparacionProgressParser {

    /** 25%|##5       | 3/12 [00:03<00:10,  1.11s/it] */
    private static final Pattern BARRA = Pattern.compile(
            "^\\s*(\\d{1,3})%\\|[^|]*\\|\\s*([0-9.]+[kKMG]?)/([0-9.]+[kKMG]?)"
                    + "\\s*\\[([0-9:]+)<([0-9:?]+),\\s*([^\\]]*)\\]");

    /** Las líneas de registro vienen como fecha - NIVEL - módulo - mensaje. */
    private static final Pattern REGISTRO = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2} [0-9:,.]+ - (\\w+) - ([\\w_]+) - (.*)$");

    private static final Pattern GUARDANDO = Pattern.compile("^Saving (.+) stem to (.+)\\.\\.\\.$");

    /**
     * Parte de la barra que se reserva por si viene otra pasada. Algunos modelos hacen dos
     * —MDX saca cada pista en una— y no lo dicen hasta que empieza la segunda, así que la
     * primera se queda en el 90 % y lo que venga después se reparte lo que falta.
     */
    private static final double RESERVA = 0.1;

    private DownloadPhase fase = DownloadPhase.PREPARANDO;
    private String dispositivo;
    private final List<String> salidas = new ArrayList<>();

    private int pasada;
    private int ultimoPorciento = -1;
    private double ultimaFraccion = -1;

    /** Fase en la que está ahora mismo. */
    public DownloadPhase fase() {
        return fase;
    }

    /** Dispositivo que ha dicho el motor que está usando, o null si todavía no lo ha dicho. */
    public String dispositivo() {
        return dispositivo;
    }

    /**
     * Nombres de los archivos que va escribiendo. Se toman de las líneas «Saving … stem to …» y no
     * de la última del programa, que los pone todos seguidos separados por espacios: con un título
     * que lleve espacios no habría forma de saber dónde acaba uno.
     */
    public List<String> salidas() {
        return List.copyOf(salidas);
    }

    /**
     * Incorpora una línea.
     *
     * @return el progreso actualizado, o vacío si la línea no aporta nada
     */
    public Optional<ProgressSnapshot> procesar(String linea) {
        if (linea == null || linea.isBlank()) {
            return Optional.empty();
        }
        Matcher barra = BARRA.matcher(linea);
        if (barra.find()) {
            return Optional.of(deBarra(barra));
        }
        Matcher registro = REGISTRO.matcher(linea.strip());
        if (registro.matches()) {
            return deRegistro(registro.group(3).strip());
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ interno

    private ProgressSnapshot deBarra(Matcher barra) {
        int porciento = Integer.parseInt(barra.group(1));
        String ritmo = barra.group(6);
        boolean esDescarga = ritmo.contains("B/s");
        fase = esDescarga ? DownloadPhase.DESCARGANDO_MODELO : DownloadPhase.SEPARANDO;

        double fraccion = esDescarga ? porciento / 100.0 : deLaPasada(porciento);
        ultimaFraccion = fraccion;

        long bytes = esDescarga ? aBytes(barra.group(2)) : 0;
        long total = esDescarga ? aBytes(barra.group(3)) : 0;
        Long restantes = aSegundos(barra.group(5));
        return new ProgressSnapshot(fase, fraccion, bytes, total, 0, restantes);
    }

    /**
     * Coloca el porcentaje de la barra que está escribiendo el motor dentro del total.
     *
     * Que el porcentaje baje significa que ha arrancado otra pasada: la anterior se da por
     * hecha y la nueva se reparte lo que quedaba. Así la barra no vuelve al principio a mitad
     * de la separación, que era lo que pasaba con los modelos de dos pasadas.
     */
    private double deLaPasada(int porciento) {
        if (porciento < ultimoPorciento) {
            pasada++;
        }
        ultimoPorciento = porciento;
        double sinRecorrer = Math.pow(RESERVA, pasada);
        return (1 - sinRecorrer) + sinRecorrer * (1 - RESERVA) * porciento / 100.0;
    }

    private Optional<ProgressSnapshot> deRegistro(String mensaje) {
        if (mensaje.startsWith("CUDA is available in Torch")) {
            dispositivo = "GPU · CUDA";
        } else if (mensaje.contains("DirectML")) {
            dispositivo = "GPU · DirectML";
        } else if (mensaje.startsWith("No hardware acceleration")
                || mensaje.contains("setting Torch device to CPU")) {
            dispositivo = "CPU";
        }

        if (mensaje.startsWith("Loading model ")) {
            return cambiarFase(DownloadPhase.CARGANDO_MODELO);
        }
        if (mensaje.startsWith("Starting separation process")) {
            return cambiarFase(DownloadPhase.SEPARANDO);
        }
        Matcher guardando = GUARDANDO.matcher(mensaje);
        if (guardando.matches()) {
            salidas.add(guardando.group(2));
            return cambiarFase(DownloadPhase.GUARDANDO_PISTAS);
        }
        if (mensaje.startsWith("Separation complete!")) {
            return cambiarFase(DownloadPhase.TERMINADA);
        }
        return Optional.empty();
    }

    /**
     * Cambia de fase conservando lo que llevaba la barra. Si se pusiera a indeterminada, al
     * guardar las pistas se desharía todo el avance que se acaba de enseñar.
     */
    private Optional<ProgressSnapshot> cambiarFase(DownloadPhase nueva) {
        if (fase == nueva) {
            return Optional.empty();
        }
        fase = nueva;
        return Optional.of(new ProgressSnapshot(nueva, ultimaFraccion, 0, 0, 0, null));
    }

    /** 639M, 1.03M, 28.3k → bytes. */
    static long aBytes(String texto) {
        if (texto == null || texto.isBlank()) {
            return 0;
        }
        char ultima = texto.charAt(texto.length() - 1);
        long factor = switch (Character.toLowerCase(ultima)) {
            case 'k' -> 1024L;
            case 'm' -> 1024L * 1024;
            case 'g' -> 1024L * 1024 * 1024;
            default -> 1;
        };
        String numero = factor == 1 ? texto : texto.substring(0, texto.length() - 1);
        try {
            return (long) (Double.parseDouble(numero) * factor);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 00:10 o 01:02:03 → segundos; ? cuando aún no se sabe. */
    static Long aSegundos(String texto) {
        if (texto == null || texto.contains("?")) {
            return null;
        }
        String[] partes = texto.split(":");
        long total = 0;
        try {
            for (String parte : partes) {
                total = total * 60 + Long.parseLong(parte);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return total;
    }
}
