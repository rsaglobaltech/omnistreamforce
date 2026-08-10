package de.omnistreamforce.cli.console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Preguntas por consola con valor por defecto, validacion y reintento.
 * <p>
 * La entrada y la salida se inyectan para poder ejercitar el flujo interactivo completo en los
 * tests con un guion de respuestas, sin terminal.
 */
public class Prompter {

    /** El usuario pidio abandonar el flujo. */
    public static class AbortedException extends RuntimeException {
        public AbortedException(String message) {
            super(message);
        }
    }

    private final BufferedReader in;
    private final PrintStream out;

    public Prompter() {
        this(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out);
    }

    public Prompter(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    /** Texto libre con valor por defecto; vacio acepta el defecto. */
    public String ask(String question, String defaultValue) {
        String suffix = defaultValue == null || defaultValue.isBlank() ? "" : " [" + defaultValue + "]";
        while (true) {
            out.print("? " + question + suffix + ": ");
            out.flush();
            String line = readLine();
            if (line.isBlank()) {
                if (defaultValue != null) {
                    return defaultValue;
                }
                out.println("  (requerido)");
                continue;
            }
            return line.trim();
        }
    }

    public String askRequired(String question) {
        return ask(question, null);
    }

    /** Texto validado; si no pasa el predicado se vuelve a preguntar. */
    public String ask(String question, String defaultValue, Predicate<String> valid, String error) {
        while (true) {
            String value = ask(question, defaultValue);
            if (valid.test(value)) {
                return value;
            }
            out.println("  " + error);
        }
    }

    public int askInt(String question, int defaultValue, int min, int max) {
        while (true) {
            String raw = ask(question, String.valueOf(defaultValue));
            try {
                int value = Integer.parseInt(raw.trim());
                if (value < min || value > max) {
                    out.println("  Debe estar entre " + min + " y " + max);
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                out.println("  No es un numero valido: " + raw);
            }
        }
    }

    public double askDouble(String question, double defaultValue, double min, double max) {
        while (true) {
            String raw = ask(question, String.valueOf(defaultValue));
            try {
                double value = Double.parseDouble(raw.trim().replace(',', '.'));
                if (value < min || value > max) {
                    out.println("  Debe estar entre " + min + " y " + max);
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                out.println("  No es un numero valido: " + raw);
            }
        }
    }

    /** Confirmacion s/n. */
    public boolean confirm(String question, boolean defaultValue) {
        String suffix = defaultValue ? " (S/n)" : " (s/N)";
        while (true) {
            out.print("? " + question + suffix + ": ");
            out.flush();
            String line = readLine().trim().toLowerCase(Locale.ROOT);
            if (line.isEmpty()) {
                return defaultValue;
            }
            if (line.startsWith("s") || line.startsWith("y")) {
                return true;
            }
            if (line.startsWith("n")) {
                return false;
            }
            out.println("  Responde s o n");
        }
    }

    /**
     * Menu numerado. Devuelve el indice (base 0) de la opcion elegida.
     */
    public int choose(String question, List<String> options, int defaultIndex) {
        for (int i = 0; i < options.size(); i++) {
            out.println("  [" + (i + 1) + "] " + options.get(i));
        }
        int choice = askInt(question, defaultIndex + 1, 1, options.size());
        return choice - 1;
    }

    /**
     * Seleccion multiple por numeros separados por comas ({@code 1,3,4}); vacio devuelve la
     * seleccion por defecto.
     */
    public List<Integer> chooseMany(String question, List<String> options, List<Integer> defaultIndexes) {
        for (int i = 0; i < options.size(); i++) {
            out.println("  [" + (i + 1) + "] " + options.get(i));
        }
        while (true) {
            out.print("? " + question + " (numeros separados por comas): ");
            out.flush();
            String line = readLine().trim();
            if (line.isEmpty()) {
                return defaultIndexes;
            }
            List<Integer> selected = new ArrayList<>();
            boolean valid = true;
            for (String token : line.split(",")) {
                try {
                    int index = Integer.parseInt(token.trim()) - 1;
                    if (index < 0 || index >= options.size()) {
                        valid = false;
                        break;
                    }
                    if (!selected.contains(index)) {
                        selected.add(index);
                    }
                } catch (NumberFormatException e) {
                    valid = false;
                    break;
                }
            }
            if (valid && !selected.isEmpty()) {
                return selected;
            }
            out.println("  Seleccion invalida; usa numeros entre 1 y " + options.size());
        }
    }

    private String readLine() {
        try {
            String line = in.readLine();
            if (line == null) {
                // fin de entrada: el usuario cerro la sesion o el guion de test se agoto
                throw new AbortedException("Entrada terminada");
            }
            return line;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
