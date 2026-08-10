package de.omnistreamforce.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, TopicAccumulator> perTopic = new ConcurrentHashMap<>();

    public void recordEvent(String topic, boolean error, double latency, long bytes) {
        totalEvents.incrementAndGet();
        totalSent.incrementAndGet();
        totalBytes.addAndGet(Math.max(0, bytes));
        lastWindowEvents.incrementAndGet();
        accumulator(topic).recordEvent(error, latency);
        if (error) {
            totalErrors.incrementAndGet();
        }
        if (latency > 0) {
            latencyMs.add(latency);
            latencyCount.incrementAndGet();
        }
    }

    public void recordFailure(String topic) {
        accumulator(topic).recordFailure();
    }

    private TopicAccumulator accumulator(String topic) {
        return perTopic.computeIfAbsent(topic, k -> new TopicAccumulator());
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
                perTopicSnapshot()
        );
    }

    private Map<String, TopicStats> perTopicSnapshot() {
        Map<String, TopicStats> snapshot = new LinkedHashMap<>();
        perTopic.forEach((topic, accumulator) -> snapshot.put(topic, accumulator.snapshot()));
        return snapshot;
    }

    /**
     * Reinicia la ventana de calculo de EPS.
     */
    public void tickWindow() {
        lastWindowEvents.set(0);
        lastWindowStartMs.set(System.currentTimeMillis());
    }

    /**
     * Acumulador mutable y thread-safe de las metricas de un topic.
     * La media de latencia se calcula al tomar el snapshot, no en cada evento.
     */
    private static final class TopicAccumulator {
        private final AtomicLong sent = new AtomicLong();
        private final AtomicLong acknowledged = new AtomicLong();
        private final AtomicLong failed = new AtomicLong();
        private final AtomicLong errors = new AtomicLong();
        private final DoubleAdder latencySum = new DoubleAdder();
        private final AtomicLong latencyCount = new AtomicLong();

        void recordEvent(boolean error, double latency) {
            sent.incrementAndGet();
            acknowledged.incrementAndGet();
            if (error) {
                errors.incrementAndGet();
            }
            if (latency > 0) {
                latencySum.add(latency);
                latencyCount.incrementAndGet();
            }
        }

        void recordFailure() {
            sent.incrementAndGet();
            failed.incrementAndGet();
        }

        TopicStats snapshot() {
            long count = latencyCount.get();
            return new TopicStats(
                    sent.get(),
                    acknowledged.get(),
                    failed.get(),
                    errors.get(),
                    count > 0 ? latencySum.sum() / count : 0.0);
        }
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