package de.omnistreamforce.domain;

/**
 * Contexto de configuracion del dominio (frecuencia, topic destino, error rate, etc.).
 * Se completa durante el flujo interactivo o batch.
 */
public record DomainContext(
        String domainName,
        String topicName,
        String errorTopicName,
        int eventsPerSecond,
        double errorRate
) {
}