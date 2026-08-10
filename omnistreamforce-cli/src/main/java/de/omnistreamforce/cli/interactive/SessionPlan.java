package de.omnistreamforce.cli.interactive;

import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.engine.PublishingMode;
import de.omnistreamforce.kafka.KafkaConnectionConfig;
import de.omnistreamforce.persistence.PersistenceConfig;
import de.omnistreamforce.persistence.SinkType;

import java.util.List;

/**
 * Lo que el usuario ha decidido durante la sesion interactiva. Es un valor puro: se puede
 * inspeccionar en los tests sin publicar nada.
 *
 * @param persistence configuracion de base de datos; null cuando el destino es solo Kafka
 */
public record SessionPlan(
        KafkaConnectionConfig kafka,
        SinkType sinkType,
        PersistenceConfig persistence,
        List<DomainPlan> domains,
        String serializerFormat,
        PublishingMode publishingMode,
        long durationSeconds,
        KeyStrategy keyStrategy,
        String keyField
) {

    public SessionPlan {
        domains = List.copyOf(domains);
    }

    /**
     * Configuracion elegida para un dominio concreto.
     */
    public record DomainPlan(
            String domain,
            String topic,
            String errorTopic,
            int eventsPerSecond,
            double errorRate
    ) {
        public boolean hasSeparateErrorTopic() {
            return errorTopic != null && !errorTopic.isBlank() && !errorTopic.equals(topic);
        }
    }

    public int totalEventsPerSecond() {
        return domains.stream().mapToInt(DomainPlan::eventsPerSecond).sum();
    }
}
