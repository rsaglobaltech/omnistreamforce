package de.omnistreamforce.persistence.relay;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Estadisticas del relay. {@code lagSeconds} es la metrica propia del patron outbox: cuanto
 * tarda un evento desde que se escribe en la tabla hasta que sale hacia Kafka.
 */
public final class RelayStats {

    private final AtomicLong polls = new AtomicLong();
    private final AtomicLong claimed = new AtomicLong();
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong deadLettered = new AtomicLong();
    private final AtomicLong batches = new AtomicLong();
    private final AtomicLong purged = new AtomicLong();
    private final DoubleAdder publishLatencyMs = new DoubleAdder();
    private final AtomicLong publishLatencyCount = new AtomicLong();

    private volatile long pendingCount;
    private volatile double lagSeconds;

    void recordPoll() {
        polls.incrementAndGet();
    }

    void recordClaimed(int rows) {
        claimed.addAndGet(rows);
        batches.incrementAndGet();
    }

    void recordPublished(int rows, double latencyMs) {
        published.addAndGet(rows);
        if (latencyMs > 0) {
            publishLatencyMs.add(latencyMs);
            publishLatencyCount.incrementAndGet();
        }
    }

    void recordFailed(int rows) {
        failed.addAndGet(rows);
    }

    void recordDeadLettered(int rows) {
        deadLettered.addAndGet(rows);
    }

    void recordPurged(int rows) {
        purged.addAndGet(rows);
    }

    void recordLag(long pending, double seconds) {
        this.pendingCount = pending;
        this.lagSeconds = seconds;
    }

    public long polls() {
        return polls.get();
    }

    public long claimed() {
        return claimed.get();
    }

    public long published() {
        return published.get();
    }

    public long failed() {
        return failed.get();
    }

    public long deadLettered() {
        return deadLettered.get();
    }

    public long batches() {
        return batches.get();
    }

    public long purged() {
        return purged.get();
    }

    public long pendingCount() {
        return pendingCount;
    }

    public double lagSeconds() {
        return lagSeconds;
    }

    public double avgBatchSize() {
        long b = batches.get();
        return b > 0 ? (double) claimed.get() / b : 0.0;
    }

    public double avgPublishLatencyMs() {
        long count = publishLatencyCount.get();
        return count > 0 ? publishLatencyMs.sum() / count : 0.0;
    }

    @Override
    public String toString() {
        return "RelayStats[published=" + published() + ", failed=" + failed()
                + ", deadLettered=" + deadLettered() + ", pending=" + pendingCount()
                + ", lagSeconds=" + String.format("%.2f", lagSeconds())
                + ", avgBatch=" + String.format("%.1f", avgBatchSize()) + "]";
    }
}
