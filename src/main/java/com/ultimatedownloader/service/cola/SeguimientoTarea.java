package com.ultimatedownloader.service.cola;

import com.ultimatedownloader.model.DownloadPhase;
import com.ultimatedownloader.model.ProgressSnapshot;
import com.ultimatedownloader.service.ProcessRunner;
import java.nio.file.Path;

/**
 * Lo que usa un ejecutor para contarle a la cola cómo va. Todo acaba en el hilo de JavaFX; el
 * ejecutor no se preocupa de eso.
 */
public interface SeguimientoTarea {

    /** Deja constancia del proceso lanzado para que la cola pueda cancelarlo. */
    void registrarProceso(ProcessRunner.ProcesoEnCurso proceso);

    void fase(DownloadPhase fase);

    void progreso(ProgressSnapshot instantanea);

    /** Ruta del archivo definitivo, en cuanto se conoce. */
    void archivoFinal(Path ruta);

    /** Dónde se está ejecutando: «CUDA · RTX 5070», «CPU»… */
    void dispositivo(String descripcion);

    /** Manda una línea en bruto a la consola común. */
    void consola(String linea);

    /** true si el usuario ha pedido cancelar. */
    boolean cancelado();
}
