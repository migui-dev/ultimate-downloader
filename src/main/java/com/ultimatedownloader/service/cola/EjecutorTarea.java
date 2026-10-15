package com.ultimatedownloader.service.cola;

import com.ultimatedownloader.model.Tarea;

/**
 * Lo que aporta cada módulo para vivir en la cola común: lanzar su proceso, contar cómo va y
 * recoger lo que ensucie. De los turnos, la cancelación y los reintentos se encarga la cola.
 */
public interface EjecutorTarea {

    /** Ejecuta la tarea y vuelve cuando ha terminado. */
    ResultadoTarea ejecutar(Tarea tarea, SeguimientoTarea seguimiento) throws InterruptedException;

    /** Borra los temporales que haya dejado la tarea al cancelarse o fallar. */
    void limpiar(Tarea tarea);

    /**
     * Prepara un reintento. Por ejemplo, una descarga recalcula el nombre del archivo por si
     * mientras tanto ha aparecido otro con el mismo.
     */
    default void prepararReintento(Tarea tarea) {
        // La mayoría de las tareas se pueden repetir tal cual.
    }
}
