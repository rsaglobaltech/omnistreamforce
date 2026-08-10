package de.omnistreamforce.persistence.metrics;

import de.omnistreamforce.engine.TopicStats;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.IntSupplier;

/**
 * Metricas del sink de base de datos. Expone {@code perTopicStats()} con la misma forma que
 * {@code KafkaEventPublisher.Metrics} para que un dashboard pueda tratar ambos sinks igual.
 */
public final class PersistenceMetrics {

    private final AtomicLong totalEnqueued = new AtomicLong();
    private final AtomicLong totalWritten = new AtomicLong();
    private final AtomicLong totalFailed = new AtomicLong();
    private final AtomicLong totalDropped = new AtomicLong();
    private final AtomicLong totalRetries = new AtomicLong();
    private final AtomicLong batchesCommitted = new AtomicLong();
    private final AtomicLong circuitOpenCount = new AtomicLong();
    private final DoubleAdder commitLatencyMs = new DoubleAdder();
    private final AtomicLong commitLatencyCount = new AtomicLong();
    private final Map<String, TopicAccumulator> perTopic = new ConcurrentHashMap<>();

    private volatile IntSupplier queueDepthSupplier = () -> 0;

    public void bindQueueDepth(IntSupplier supplier) {
        this.queueDepthSupplier = supplier == null ? () -> 0 : supplier;
    }

    public void recordEnqueued(String topic) {
        totalEnqueued.incrementAndGet();
        accumulator(topic).sent.incrementAndGet();
    }

    public void recordWritten(String topic, double commitLatencyMs) {
        totalWritten.incrementAndGet();
        TopicAccumulator accumulator = accumulator(topic);
        accumulator.acknowledged.incrementAndGet();
        if (commitLatencyMs > 0) {
            accumulator.latencySum.add(commitLatencyMs);
            accumulator.latencyCount.incrementAndGet();
        }
    }

    public void recordFailed(String topic) {
        totalFailed.incrementAndGet();
        accumulator(topic).failed.incrementAndGet();
    }

    public void recordDropped(String topic) {
        totalDropped.incrementAndGet();
        accumulator(topic).failed.incrementAndGet();
    }

    public void recordRetry() {
        totalRetries.incrementAndGet();
    }

    public void recordBatchCommitted(int rows, double latencyMs) {
        batchesCommitted.incrementAndGet();
        if (latencyMs > 0) {
            commitLatencyMs.add(latencyMs);
            commitLatencyCount.incrementAndGet();
        }
    }

    public void recordCircuitOpened() {
        circuitOpenCount.incrementAndGet();
    }

    private TopicAccumulator accumulator(String topic) {
        return perTopic.computeIfAbsent(topic == null ? "?" : topic, k -> new TopicAccumulator());
    }

    public long totalEnqueued() {
        return totalEnqueued.get();
    }

    public long totalWritten() {
        return totalWritten.get();
    }

    public long totalFailed() {
        return totalFailed.get();
    }

    public long totalDropped() {
        return totalDropped.get();
    }

    public long totalRetries() {
        return totalRetries.get();
    }

    public long batchesCommitted() {
        return batchesCommitted.get();
    }

    public long circuitOpenCount() {
        return circuitOpenCount.get();
    }

    public int queueDepth() {
        return queueDepthSupplier.getAsInt();
    }

    public double avgBatchSize() {
        long batches = batchesCommitted.get();
        return batches > 0 ? (double) totalWritten.get() / batches : 0.0;
    }

    public double avgCommitLatencyMs() {
        long count = commitLatencyCount.get();
        return count > 0 ? commitLatencyMs.sum() / count : 0.0;
    }

    public Map<String, TopicStats> perTopicStats() {
        Map<String, TopicStats> snapshot = new LinkedHashMap<>();
        perTopic.forEach((topic, accumulator) -> snapshot.put(topic, accumulator.snapshot()));
        return snapshot;
    }

    private static final class TopicAccumulator {
        private final AtomicLong sent = new AtomicLong();
        private final AtomicLong acknowledged = new AtomicLong();
        private final AtomicLong failed = new AtomicLong();
        private final DoubleAdder latencySum = new DoubleAdder();
        private final AtomicLong latencyCount = new AtomicLong();

        TopicStats snapshot() {
            long count = latencyCount.get();
            return new TopicStats(sent.get(), acknowledged.get(), failed.get(), 0,
                    count > 0 ? latencySum.sum() / count : 0.0);
        }
    }
}
