package de.omnistreamforce.cli.console;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

/**
 * Salida por consola: tablas, colores y limpieza de pantalla.
 * <p>
 * Detecta si el terminal admite ANSI y, si no, degrada a texto plano en vez de ensuciar la
 * salida con codigos de escape (caso tipico: cmd.exe antiguo o salida redirigida a un fichero).
 */
public class ConsoleRenderer {

    private static final String RESET = "[0m";
    private static final String BOLD = "[1m";
    private static final String DIM = "[2m";
    private static final String RED = "[31m";
    private static final String GREEN = "[32m";
    private static final String YELLOW = "[33m";
    private static final String CYAN = "[36m";

    private final PrintStream out;
    private final boolean ansi;

    public ConsoleRenderer() {
        this(System.out, detectAnsiSupport());
    }

    public ConsoleRenderer(PrintStream out, boolean ansi) {
        this.out = out;
        this.ansi = ansi;
    }

    /**
     * Windows 10+ y las terminales Unix admiten ANSI; sin consola adjunta (salida redirigida)
     * se desactiva.
     */
    static boolean detectAnsiSupport() {
        if (System.console() == null) {
            return false;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return true;
        }
        // Windows Terminal, PowerShell 7 y ConEmu exportan estas variables
        return System.getenv("WT_SESSION") != null
                || System.getenv("ConEmuANSI") != null
                || "xterm-256color".equals(System.getenv("TERM"));
    }

    public boolean ansiEnabled() {
        return ansi;
    }

    public PrintStream out() {
        return out;
    }

    public void println() {
        out.println();
    }

    public void println(String text) {
        out.println(text);
    }

    public void title(String text) {
        out.println();
        out.println(color(BOLD, text));
        out.println(color(DIM, "-".repeat(Math.max(4, text.length()))));
    }

    public void step(int number, String text) {
        out.println();
        out.println(color(CYAN + BOLD, "Paso " + number + ": " + text));
        out.println(color(DIM, "-".repeat(Math.max(8, text.length() + 8))));
    }

    public void success(String text) {
        out.println(color(GREEN, "[OK] ") + text);
    }

    public void warn(String text) {
        out.println(color(YELLOW, "[!] ") + text);
    }

    public void error(String text) {
        out.println(color(RED, "[ERROR] ") + text);
    }

    public void info(String text) {
        out.println("  " + text);
    }

    /** Limpia la pantalla; sin ANSI se limita a separar con lineas en blanco. */
    public void clear() {
        if (ansi) {
            out.print("[H[2J");
            out.flush();
        } else {
            out.println(System.lineSeparator().repeat(3));
        }
    }

    /**
     * Tabla con anchos calculados a partir del contenido.
     */
    public void table(List<String> headers, List<List<String>> rows) {
        out.println(renderTable(headers, rows));
    }

    public String renderTable(List<String> headers, List<List<String>> rows) {
        int columns = headers.size();
        int[] widths = new int[columns];
        for (int i = 0; i < columns; i++) {
            widths[i] = headers.get(i).length();
        }
        for (List<String> row : rows) {
            for (int i = 0; i < columns && i < row.size(); i++) {
                String cell = row.get(i) == null ? "" : row.get(i);
                widths[i] = Math.max(widths[i], cell.length());
            }
        }

        StringBuilder sb = new StringBuilder();
        appendSeparator(sb, widths, '+');
        appendRow(sb, headers, widths);
        appendSeparator(sb, widths, '+');
        for (List<String> row : rows) {
            appendRow(sb, row, widths);
        }
        appendSeparator(sb, widths, '+');
        return sb.toString();
    }

    private void appendSeparator(StringBuilder sb, int[] widths, char corner) {
        sb.append(corner);
        for (int width : widths) {
            sb.append("-".repeat(width + 2)).append(corner);
        }
        sb.append(System.lineSeparator());
    }

    private void appendRow(StringBuilder sb, List<String> cells, int[] widths) {
        sb.append('|');
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.size() && cells.get(i) != null ? cells.get(i) : "";
            sb.append(' ').append(pad(cell, widths[i])).append(" |");
        }
        sb.append(System.lineSeparator());
    }

    private String pad(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }

    /** Barra de progreso de ancho fijo: {@code ####------ 40%}. */
    public String progressBar(double ratio, int width) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        int filled = (int) Math.round(clamped * width);
        return "[" + "#".repeat(filled) + "-".repeat(width - filled) + "] "
                + String.format(Locale.ROOT, "%3.0f%%", clamped * 100);
    }

    /** Formatea cantidades grandes: 1500 -> 1.5K, 2300000 -> 2.3M. */
    public static String humanize(long value) {
        if (value < 1_000) {
            return String.valueOf(value);
        }
        if (value < 1_000_000) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }

    private String color(String code, String text) {
        return ansi ? code + text + RESET : text;
    }
}
