package de.omnistreamforce.routing;

/**
 * Mapeo de un dominio a su(s) topic(s) de destino en Kafka.
 */
public record TopicMapping(
        String domain,
        String topicName,
        String errorTopicName,
        int partitions,
        short replicationFactor,
        boolean autoCreate
) {
    public TopicMapping {
        if (partitions < 1) {
            partitions = 1;
        }
        if (replicationFactor < 1) {
            replicationFactor = 1;
        }
    }

    public boolean hasErrorTopic() {
        return errorTopicName != null && !errorTopicName.isBlank();
    }
}