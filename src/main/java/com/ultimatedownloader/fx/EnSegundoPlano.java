package com.ultimatedownloader.fx;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/** Lanza trabajo fuera del hilo de la interfaz y devuelve el resultado dentro de él. */
public final class EnSegundoPlano {

    private EnSegundoPlano() {
    }

    /** Trabajo que devuelve algo: alTerminar lo recibe ya en el hilo de la interfaz. */
    public static <T> void calcular(Executor ejecutor, Callable<T> trabajo,
                                    Consumer<T> alTerminar, Consumer<Throwable> siFalla) {
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabajo.call();
            }
        };
        tarea.setOnSucceeded(e -> alTerminar.accept(tarea.getValue()));
        tarea.setOnFailed(e -> siFalla.accept(tarea.getException()));
        ejecutor.execute(tarea);
    }

    /** Trabajo que no devuelve nada. */
    public static void hacer(Executor ejecutor, Trabajo trabajo,
                             Runnable alTerminar, Consumer<Throwable> siFalla) {
        calcular(ejecutor, () -> {
            trabajo.ejecutar();
            return null;
        }, nada -> alTerminar.run(), siFalla);
    }

    /** Como Runnable, pero puede fallar. */
    @FunctionalInterface
    public interface Trabajo {
        void ejecutar() throws Exception;
    }
}
