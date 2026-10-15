package com.ultimatedownloader.ui;

import com.ultimatedownloader.util.Formatos;
import java.util.function.Function;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

/**
 * Lo que se repite al manejar controles: leer números de un campo y dar formato a un
 * desplegable. Un campo vacío o mal escrito vale null, que para el motor es «no tocar esto».
 */
public final class Controles {

    private Controles() {
    }

    public static Integer entero(TextField campo) {
        return entero(campo.getText());
    }

    public static int entero(TextField campo, int porDefecto) {
        Integer valor = entero(campo);
        return valor != null ? valor : porDefecto;
    }

    public static Double decimal(TextField campo) {
        return decimal(campo.getText());
    }

    public static void escribir(TextField campo, Number valor) {
        campo.setText(valor == null ? "" : Formatos.numeroSuelto(valor));
    }

    /** Lo que haya escrito, o null si está vacío o no es un número entero. */
    static Integer entero(String texto) {
        String limpio = limpiar(texto);
        if (limpio == null) {
            return null;
        }
        try {
            return Integer.valueOf(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Igual que entero(String), admitiendo la coma decimal del español. */
    static Double decimal(String texto) {
        String limpio = limpiar(texto);
        if (limpio == null) {
            return null;
        }
        try {
            return Double.valueOf(limpio.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Converter para un desplegable que solo muestra, de ahí que fromString no devuelva nada. La
     * función recibe también el null del hueco vacío, que en algunos significa «todas».
     */
    public static <T> StringConverter<T> comoTexto(Function<T, String> texto) {
        return new StringConverter<>() {
            @Override
            public String toString(T valor) {
                return texto.apply(valor);
            }

            @Override
            public T fromString(String escrito) {
                return null;
            }
        };
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
