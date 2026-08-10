package de.omnistreamforce.cli.interactive;

import de.omnistreamforce.cli.console.ConsoleRenderer;
import de.omnistreamforce.engine.GenerationStats;
import de.omnistreamforce.engine.MultiDomainEngine;
import de.omnistreamforce.engine.TopicStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Panel en vivo de la publicacion: un bloque por dominio activo y un total agregado.
 * <p>
 * El render esta separado del bucle de refresco para poder comprobarlo en los tests con
 * estadisticas fabricadas, sin arrancar ningun motor.
 */
public class PublishingDashboard {

    private static final int REFRESH_MS = 500;
    private static final int BAR_WIDTH = 30;

    private final MultiDomainEngine engine;
    private final ConsoleRenderer console;
    private final long durationSeconds;
    private final AtomicBoolean running = new AtomicBoolean();
    private final long startedAtMs = System.currentTimeMillis();

    private Thread refresher;
    private PublishingControls controls;

    public PublishingDashboard(MultiDomainEngine engine, ConsoleRenderer console, long durationSeconds) {
        this.engine = engine;
        this.console = console;
        this.durationSeconds = durationSeconds;
    }

    /** Los controles se pintan en el panel: estado actual y ultima accion aplicada. */
    public void bindControls(PublishingControls controls) {
        this.controls = controls;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        refresher = new Thread(this::loop, "osf-dashboard");
        refresher.setDaemon(true);
        refresher.start();
    }

    public void stop() {
        running.set(false);
        if (refresher != null) {
            refresher.interrupt();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void loop() {
        while (running.get()) {
            try {
                console.clear();
                console.println(render(engine.aggregateStats(), elapsedSeconds()));
                Thread.sleep(REFRESH_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // un fallo pintando nunca debe tumbar la publicacion
                return;
            }
        }
    }

    private long elapsedSeconds() {
        return (System.currentTimeMillis() - startedAtMs) / 1000;
    }

    /**
     * Render completo del panel. Es puro: mismas estadisticas, mismo texto.
     */
    public String render(MultiDomainEngine.AggregateStats stats, long elapsedSeconds) {
        boolean paused = controls != null && controls.isPaused();
        StringBuilder sb = new StringBuilder();
        sb.append("OmniStreamForce - ").append(paused ? "PAUSADO" : "publicando")
                .append(System.lineSeparator());
        sb.append("Tiempo: ").append(elapsedSeconds).append("s");
        if (durationSeconds > 0) {
            sb.append(" / ").append(durationSeconds).append("s  ")
                    .append(console.progressBar((double) elapsedSeconds / durationSeconds, BAR_WIDTH));
        }
        sb.append(System.lineSeparator()).append(System.lineSeparator());

        List<List<String>> rows = new ArrayList<>();
        for (Map.Entry<String, GenerationStats.Snapshot> entry : stats.perDomain().entrySet()) {
            GenerationStats.Snapshot snapshot = entry.getValue();
            rows.add(List.of(
                    entry.getKey(),
                    ConsoleRenderer.humanize(snapshot.totalEvents()),
                    ConsoleRenderer.humanize(snapshot.totalErrors()),
                    String.format(Locale.ROOT, "%.1f", snapshot.eventsPerSecond()),
                    String.format(Locale.ROOT, "%.1f ms", snapshot.avgLatencyMs())));
        }
        sb.append(console.renderTable(
                List.of("Dominio", "Eventos", "Errores", "evt/s", "Latencia"), rows));

        if (!stats.perTopic().isEmpty()) {
            List<List<String>> topicRows = new ArrayList<>();
            for (Map.Entry<String, TopicStats> entry : stats.perTopic().entrySet()) {
                TopicStats topic = entry.getValue();
                topicRows.add(List.of(
                        entry.getKey(),
                        ConsoleRenderer.humanize(topic.totalSent()),
                        ConsoleRenderer.humanize(topic.totalErrors()),
                        ConsoleRenderer.humanize(topic.totalFailed()),
                        String.format(Locale.ROOT, "%.1f ms", topic.avgLatencyMs())));
            }
            sb.append(System.lineSeparator());
            sb.append(console.renderTable(
                    List.of("Topic", "Enviados", "Errores", "Fallidos", "Latencia"), topicRows));
        }

        sb.append(System.lineSeparator());
        sb.append("TOTAL: ").append(ConsoleRenderer.humanize(stats.totalEvents())).append(" eventos | ")
                .append(ConsoleRenderer.humanize(stats.totalErrors())).append(" errores | ")
                .append(ConsoleRenderer.humanize(stats.totalBytes())).append(" bytes")
                .append(System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("[P] pausar  [R] reanudar  [+/-] ritmo  [E <n>] tasa de error  [S] parar  [Q] salir")
                .append(System.lineSeparator());
        // el terminal entrega la entrada por lineas: sin Enter el comando no llega
        sb.append("(escribe la letra y pulsa Enter)").append(System.lineSeparator());
        if (controls != null && !controls.lastAction().isEmpty()) {
            sb.append("Ultima accion: ").append(controls.lastAction()).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
