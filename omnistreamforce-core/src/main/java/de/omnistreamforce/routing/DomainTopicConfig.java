package de.omnistreamforce.routing;

import java.util.List;

/**
 * Configuracion de routing que agrupa todos los mapeos dominio -> topic.
 */
public record DomainTopicConfig(
        List<TopicMapping> mappings,
        boolean separateErrorTopics,
        String errorTopicSuffix
) {
    public DomainTopicConfig {
        if (mappings == null) {
            mappings = List.of();
        } else {
            mappings = List.copyOf(mappings);
        }
        if (errorTopicSuffix == null || errorTopicSuffix.isBlank()) {
            errorTopicSuffix = "-errors";
        }
    }
}