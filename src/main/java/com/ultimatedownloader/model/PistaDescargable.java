package com.ultimatedownloader.model;

/**
 * Lo que comparten las pistas que se eligen en las tablas de Descargar, para que la de vídeo
 * y la de audio se construyan con el mismo código.
 */
public interface PistaDescargable {

    /** La fila «Mejor disponible»: no es una pista de verdad, la elige yt-dlp al descargar. */
    boolean esMejorDisponible();
}
