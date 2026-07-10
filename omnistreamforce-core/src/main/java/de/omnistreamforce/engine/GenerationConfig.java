package de.omnistreamforce.engine;

/**
 * Configuracion de generacion para un dominio concreto.
 */
public record GenerationConfig(
        String domainName,
        String topicName,
        String errorTopicName,
        int eventsPerSecond,
        double errorRate,
        PublishingMode publishingMode,
        long durationSeconds,
        KeyStrategy keyStrategy,
        String keyField,
        int burstSize,
        int rampTargetEPS
) {
    public GenerationConfig {
        if (eventsPerSecond < 0) {
            eventsPerSecond = 0;
        }
        if (errorRate < 0.0) {
            errorRate = 0.0;
        } else if (errorRate > 100.0) {
            errorRate = 100.0;
        }
        if (durationSeconds < 0) {
            durationSeconds = 0;
        }
        if (publishingMode == null) {
            publishingMode = PublishingMode.STEADY;
        }
        if (keyStrategy == null) {
            keyStrategy = KeyStrategy.RANDOM;
        }
    }

    public boolean isUnlimited() {
        return durationSeconds == 0;
    }
}