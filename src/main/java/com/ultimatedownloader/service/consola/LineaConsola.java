package com.ultimatedownloader.service.consola;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Una línea de la consola, con quién la escribe (yt-dlp, ffmpeg, app…) y su gravedad. */
public record LineaConsola(LocalTime instante, String tarea, NivelConsola nivel, String texto) {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static LineaConsola de(String tarea, NivelConsola nivel, String texto) {
        return new LineaConsola(LocalTime.now(), tarea, nivel, texto);
    }

    public String horaTexto() {
        return instante.format(HORA);
    }

    /** Línea completa para copiar al portapapeles o guardar en un archivo. */
    public String comoTexto() {
        return horaTexto() + "  " + tarea + "  " + texto;
    }
}
