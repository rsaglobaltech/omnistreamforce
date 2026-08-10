package de.omnistreamforce.cli.interactive;

import de.omnistreamforce.engine.GenerationEngine;
import de.omnistreamforce.engine.MultiDomainEngine;

import java.io.BufferedReader;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * Controles de teclado del panel de publicacion.
 * <p>
 * El terminal entrega la entrada por lineas, asi que cada comando se confirma con Enter; el panel
 * lo indica. El estado es observable para que el panel muestre si esta pausado y cual fue la
 * ultima accion: sin esa realimentacion parecia que las teclas no hacian nada.
 */
public class PublishingControls implements Runnable {

    /** Ajuste relativo del ritmo por pulsacion. */
    private static final double SPEED_STEP = 0.25;

    private final MultiDomainEngine engine;
    private final BufferedReader in;
    private final CountDownLatch stopRequested;

    private volatile boolean paused;
    private volatile String lastAction = "";

    public PublishingControls(MultiDomainEngine engine, BufferedReader in, CountDownLatch stopRequested) {
        this.engine = engine;
        this.in = in;
        this.stopRequested = stopRequested;
    }

    public boolean isPaused() {
        return paused;
    }

    public String lastAction() {
        return lastAction;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (!handle(line)) {
                    return;
                }
            }
        } catch (Exception e) {
            lastAction = "entrada no disponible: " + e.getMessage();
        }
        // entrada agotada (tuberia cerrada): no se fuerza la parada, manda la duracion configurada
    }

    /**
     * Aplica un comando. Devuelve false cuando se ha pedido terminar.
     */
    public boolean handle(String rawLine) {
        String command = rawLine == null ? "" : rawLine.trim().toLowerCase(Locale.ROOT);
        if (command.isEmpty()) {
            return true;
        }
        switch (command.charAt(0)) {
            case 'p' -> {
                engine.pauseAll();
                paused = true;
                lastAction = "pausado";
            }
            case 'r' -> {
                engine.resumeAll();
                paused = false;
                lastAction = "reanudado";
            }
            case '+' -> lastAction = "ritmo " + scaleSpeed(1 + SPEED_STEP) + " evt/s";
            case '-' -> lastAction = "ritmo " + scaleSpeed(1 - SPEED_STEP) + " evt/s";
            case 'e' -> lastAction = applyErrorRate(command);
            case 's', 'q' -> {
                lastAction = "parando";
                stopRequested.countDown();
                return false;
            }
            default -> lastAction = "comando desconocido: " + command;
        }
        return true;
    }

    /** Escala el ritmo de todos los dominios activos y devuelve el total resultante. */
    private int scaleSpeed(double factor) {
        int total = 0;
        for (MultiDomainEngine.DomainEntry entry : engine.domainEntries()) {
            GenerationEngine domainEngine = entry.engine();
            int current = domainEngine.currentEventsPerSecond();
            int updated = (int) Math.round(current * factor);
            if (updated == current) {
                // con ritmos bajos el redondeo no mueve nada: se fuerza un paso de una unidad
                updated = factor > 1 ? current + 1 : current - 1;
            }
            updated = Math.max(1, updated);
            domainEngine.setEventsPerSecond(updated);
            total += updated;
        }
        return total;
    }

    /** {@code e 25} fija la tasa de error al 25% en todos los dominios. */
    private String applyErrorRate(String command) {
        String argument = command.substring(1).trim().replace(',', '.');
        if (argument.isEmpty()) {
            return "usa 'e <porcentaje>', por ejemplo: e 25";
        }
        try {
            double rate = Double.parseDouble(argument);
            if (rate < 0 || rate > 100) {
                return "la tasa de error debe estar entre 0 y 100";
            }
            engine.domainEntries().forEach(entry -> entry.engine().setErrorRate(rate));
            return String.format(Locale.ROOT, "tasa de error al %.1f%%", rate);
        } catch (NumberFormatException e) {
            return "no es un porcentaje valido: " + argument;
        }
    }
}
