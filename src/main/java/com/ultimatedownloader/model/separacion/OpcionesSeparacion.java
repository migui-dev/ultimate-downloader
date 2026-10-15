package com.ultimatedownloader.model.separacion;

/**
 * Opciones finas del motor, las mismas que expone audio-separator. Qué hace cada una está en
 * los tooltips del panel avanzado. Los números van como objetos y no como primitivos a
 * propósito: null es «no tocar esto», y entonces manda el valor que traiga el modelo.
 */
public record OpcionesSeparacion(
        Double normalizacion,
        Double amplificacion,
        boolean invertirEspectro,
        boolean usarAutocast,
        boolean usarFp16,
        boolean usarTorchCompile,
        Integer duracionTrozo,

        Integer mdxTamanoSegmento,
        Double mdxSolapamiento,
        Integer mdxLote,
        Integer mdxHop,
        boolean mdxDenoise,

        Integer vrLote,
        Integer vrVentana,
        Integer vrAgresividad,
        boolean vrTta,
        boolean vrAgudos,
        boolean vrPostProceso,
        Double vrUmbralPostProceso,

        Integer demucsSegmento,
        Integer demucsShifts,
        Double demucsSolapamiento,

        Integer mdxcSegmento,
        boolean mdxcForzarSegmento,
        Integer mdxcSolapamiento,
        Integer mdxcLote,
        Integer mdxcTono) {

    /** Todo como lo deja la herramienta: es lo que se usa salvo que alguien toque algo. */
    public static OpcionesSeparacion porDefecto() {
        return new OpcionesSeparacion(
                null, null, false, false, false, false, null,
                null, null, null, null, false,
                null, null, null, false, false, false, null,
                null, null, null,
                null, false, null, null, null);
    }

    /** true si no se ha cambiado nada respecto a los valores del modelo. */
    public boolean sonLasPorDefecto() {
        return equals(porDefecto());
    }
}
