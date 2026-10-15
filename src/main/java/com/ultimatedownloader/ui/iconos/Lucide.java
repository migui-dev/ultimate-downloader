package com.ultimatedownloader.ui.iconos;

import java.util.List;

/**
 * Iconos de Lucide (https://lucide.dev, licencia ISC), generados por scripts/generarLucide.js.
 * Todos van sobre una rejilla de 24×24 con trazo de 2, así que comparten grosor y geometría.
 *
 * Cada icono guarda sus trazos por separado, como venían: unirlos en una sola cadena rompía
 * los comandos relativos de SVG. No los edites a mano, cambia la lista del guion.
 */
public enum Lucide {

    /** external-link */
    ABRIR_FUERA(
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"),

    /** settings */
    AJUSTES(
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0"
            + " 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34"
            + " 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34"
            + " 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35"
            + " 6.051a2.34 2.34 0 0 0 3.319-1.915",
            "M9 12A3 3 0 1 0 15 12A3 3 0 1 0 9 12Z"),

    /** plus */
    ANADIR(
            "M5 12h14",
            "M12 5v14"),

    /** file-music */
    ARCHIVO_AUDIO(
            "M11.65 22H18a2 2 0 0 0 2-2V8a2.4 2.4 0 0 0-.706-1.706l-3.588-3.588A2.4 2.4 0"
            + " 0 0 14 2H6a2 2 0 0 0-2 2v10.35",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M8 20v-7l3 1.474",
            "M4 20A2 2 0 1 0 8 20A2 2 0 1 0 4 20Z"),

    /** film */
    ARCHIVO_VIDEO(
            "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z",
            "M7 3v18",
            "M3 7.5h4",
            "M3 12h18",
            "M3 16.5h4",
            "M17 3v18",
            "M17 7.5h4",
            "M17 16.5h4"),

    /** triangle-alert */
    AVISO(
            "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
            "M12 9v4",
            "M12 17h.01"),

    /** search */
    BUSCAR(
            "m21 21-4.34-4.34",
            "M3 11A8 8 0 1 0 19 11A8 8 0 1 0 3 11Z"),

    /** circle-x */
    CANCELAR(
            "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12Z",
            "m15 9-6 6",
            "m9 9 6 6"),

    /** loader-circle */
    CARGANDO(
            "M21 12a9 9 0 1 1-6.219-8.56"),

    /** folder */
    CARPETA(
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0"
            + " 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"),

    /** folder-open */
    CARPETA_ABIERTA(
            "m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95"
            + " 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0"
            + " 1.67.9H18a2 2 0 0 1 2 2v2"),

    /** x */
    CERRAR(
            "M18 6 6 18",
            "m6 6 12 12"),

    /** list-ordered */
    COLA(
            "M11 5h10",
            "M11 12h10",
            "M11 19h10",
            "M4 4h1v5",
            "M4 9h2",
            "M6.5 20H3.4c0-1 2.6-1.925 2.6-3.5a1.5 1.5 0 0 0-2.6-1.02"),

    /** terminal */
    CONSOLA(
            "M12 19h8",
            "m4 17 6-6-6-6"),

    /** arrow-left-right */
    CONVERTIR(
            "M8 3 4 7l4 4",
            "M4 7h16",
            "m16 21 4-4-4-4",
            "M20 17H4"),

    /** copy */
    COPIAR(
            "M10 8H20A2 2 0 0 1 22 10V20A2 2 0 0 1 20 22H10A2 2 0 0 1 8 20V10A2 2 0 0 1"
            + " 10 8Z",
            "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"),

    /** download */
    DESCARGAR(
            "M12 15V3",
            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
            "m7 10 5 5 5-5"),

    /** hard-drive */
    DISCO(
            "M10 16h.01",
            "M2.212 11.577a2 2 0 0 0-.212.896V18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5.527a2"
            + " 2 0 0 0-.212-.896L18.55 5.11A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z",
            "M21.946 12.013H2.054",
            "M6 16h.01"),

    /** circle-alert */
    ERROR(
            "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12Z",
            "M12 8L12 12",
            "M12 16L12.01 16"),

    /** star */
    FAVORITO(
            "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595"
            + " 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611"
            + " 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973"
            + " 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0"
            + " 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0"
            + " 1.597-1.16z"),

    /** funnel */
    FILTRAR(
            "M10 20a1 1 0 0 0 .553.895l2 1A1 1 0 0 0 14 21v-7a2 2 0 0 1 .517-1.341L21.74"
            + " 4.67A1 1 0 0 0 21 3H3a1 1 0 0 0-.742 1.67l7.225 7.989A2 2 0 0 1 10 14z"),

    /** chevron-down */
    FLECHA_ABAJO(
            "m6 9 6 6 6-6"),

    /** chevron-right */
    FLECHA_DERECHA(
            "m9 18 6-6-6-6"),

    /** save */
    GUARDAR(
            "M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0"
            + " 1-2-2V5a2 2 0 0 1 2-2z",
            "M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7",
            "M7 3v4a1 1 0 0 0 1 1h7"),

    /** check */
    HECHO(
            "M20 6 9 17l-5-5"),

    /** info */
    INFO(
            "M2 12A10 10 0 1 0 22 12A10 10 0 1 0 2 12Z",
            "M12 16v-4",
            "M12 8h.01"),

    /** moon */
    LUNA(
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0"
            + " 8.268 8.268c.344-.215.825-.004.803.401"),

    /** music */
    MUSICA(
            "M9 18V5l12-2v13",
            "M3 18A3 3 0 1 0 9 18A3 3 0 1 0 3 18Z",
            "M15 16A3 3 0 1 0 21 16A3 3 0 1 0 15 16Z"),

    /** audio-lines */
    ONDAS(
            "M2 10v3",
            "M6 6v11",
            "M10 3v18",
            "M14 8v7",
            "M18 5v13",
            "M22 10v3"),

    /** panel-bottom-open */
    PANEL_ABRIR(
            "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z",
            "M3 15h18",
            "m9 10 3-3 3 3"),

    /** panel-bottom-close */
    PANEL_CERRAR(
            "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z",
            "M3 15h18",
            "m15 8-3 3-3-3"),

    /** trash */
    PAPELERA(
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"),

    /** cpu */
    PROCESADOR(
            "M12 20v2",
            "M12 2v2",
            "M17 20v2",
            "M17 2v2",
            "M2 12h2",
            "M2 17h2",
            "M2 7h2",
            "M20 12h2",
            "M20 17h2",
            "M20 7h2",
            "M7 20v2",
            "M7 2v2",
            "M6 4H18A2 2 0 0 1 20 6V18A2 2 0 0 1 18 20H6A2 2 0 0 1 4 18V6A2 2 0 0 1 6 4Z",
            "M9 8H15A1 1 0 0 1 16 9V15A1 1 0 0 1 15 16H9A1 1 0 0 1 8 15V9A1 1 0 0 1 9 8Z"),

    /** rotate-cw */
    REINTENTAR(
            "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8",
            "M21 3v5h-5"),

    /** play */
    REPRODUCIR(
            "M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1"
            + " 5 19z"),

    /** audio-waveform */
    SEPARAR(
            "M2 13a2 2 0 0 0 2-2V7a2 2 0 0 1 4 0v13a2 2 0 0 0 4 0V4a2 2 0 0 1 4 0v13a2 2"
            + " 0 0 0 4 0v-4a2 2 0 0 1 2-2"),

    /** sun */
    SOL(
            "M8 12A4 4 0 1 0 16 12A4 4 0 1 0 8 12Z",
            "M12 2v2",
            "M12 20v2",
            "m4.93 4.93 1.41 1.41",
            "m17.66 17.66 1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "m6.34 17.66-1.41 1.41",
            "m19.07 4.93-1.41 1.41"),

    /** video */
    VIDEO(
            "m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5",
            "M4 6H14A2 2 0 0 1 16 8V16A2 2 0 0 1 14 18H4A2 2 0 0 1 2 16V8A2 2 0 0 1 4 6Z"),
    ;

    /** Lado de la rejilla en la que están dibujados todos los iconos. */
    public static final double REJILLA = 24;

    private final List<String> trazos;

    Lucide(String... trazos) {
        this.trazos = List.of(trazos);
    }

    /** Trazos del icono, cada uno en sus propios datos de ruta SVG. */
    public List<String> trazos() {
        return trazos;
    }
}
