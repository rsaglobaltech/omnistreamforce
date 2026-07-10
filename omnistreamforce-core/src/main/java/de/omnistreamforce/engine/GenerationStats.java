package de.omnistreamforce.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Estadisticas de generacion acumuladas (por dominio/topic y globales).
 * Implementacion thread-safe mediante contadores atomicos.
 */
public class GenerationStats {

    private final long startTimeMs = System.currentTimeMillis();
    private final AtomicLong totalEvents = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();
    private final AtomicLong totalSent = new AtomicLong();
    private final AtomicLong totalBytes = new AtomicLong();
    private final AtomicLong lastWindowEvents = new AtomicLong();
    private final AtomicLong lastWindowStartMs = new AtomicLong(System.currentTimeMillis());
    private final DoubleAdder latencyMs = new DoubleAdder();
    private final AtomicLong latencyCount = new AtomicLong();
    private final Map<String, TopicStats> perTopic = new HashMap<>();

    public void recordEvent(String topic, boolean error, double latency, long bytes) {
        totalEvents.incrementAndGet();
        totalSent.incrementAndGet();
        totalBytes.addAndGet(Math.max(0, bytes));
        lastWindowEvents.incrementAndGet();
        perTopic.compute(topic, (k, v) -> new TopicStats(
                (v == null ? 0 : v.totalSent()) + 1,
                (v == null ? 0 : v.totalAcknowledged()) + 1,
                (v == null ? 0 : v.totalFailed()),
                (v == null ? 0 : v.totalErrors()) + (error ? 1 : 0),
                v == null ? 0.0 : v.avgLatencyMs()
        ));
        if (error) {
            totalErrors.incrementAndGet();
        }
        if (latency > 0) {
            latencyMs.add(latency);
            latencyCount.incrementAndGet();
        }
    }

    public void recordFailure(String topic) {
        perTopic.compute(topic, (k, v) -> new TopicStats(
                (v == null ? 0 : v.totalSent()) + 1,
                (v == null ? 0 : v.totalAcknowledged()),
                (v == null ? 0 : v.totalFailed()) + 1,
                (v == null ? 0 : v.totalErrors()),
                v == null ? 0.0 : v.avgLatencyMs()
        ));
    }

    /**
     * Snapshot inmutable de las estadisticas actuales.
     */
    public Snapshot snapshot() {
        long now = System.currentTimeMillis();
        long windowStart = lastWindowStartMs.get();
        long elapsed = now - windowStart;
        double eps = elapsed > 0 ? (lastWindowEvents.get() * 1000.0) / elapsed : 0.0;
        long totalLatencyCount = latencyCount.get();
        double avgLatency = totalLatencyCount > 0 ? latencyMs.sum() / totalLatencyCount : 0.0;
        return new Snapshot(
                totalEvents.get(),
                totalErrors.get(),
                totalSent.get(),
                totalBytes.get(),
                eps,
                now - startTimeMs,
                avgLatency,
                Map.copyOf(perTopic)
        );
    }

    /**
     * Reinicia la ventana de calculo de EPS.
     */
    public void tickWindow() {
        lastWindowEvents.set(0);
        lastWindowStartMs.set(System.currentTimeMillis());
    }

    public record Snapshot(
            long totalEvents,
            long totalErrors,
            long totalSent,
            long totalBytes,
            double eventsPerSecond,
            long uptimeMs,
            double avgLatencyMs,
            Map<String, TopicStats> perTopicStats
    ) {
        public Snapshot {
            perTopicStats = perTopicStats == null ? Map.of() : Collections.unmodifiableMap(perTopicStats);
        }
    }
}