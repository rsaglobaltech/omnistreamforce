package de.omnistreamforce.engine;

/**
 * Estadisticas de un topic concreto.
 */
public record TopicStats(
        long totalSent,
        long totalAcknowledged,
        long totalFailed,
        long totalErrors,
        double avgLatencyMs
) {
    public static TopicStats empty() {
        return new TopicStats(0, 0, 0, 0, 0.0);
    }
}