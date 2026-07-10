package de.omnistreamforce.routing;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventType;

import java.util.Map;
import java.util.Objects;

/**
 * Implementacion por defecto de {@link TopicRouter} basada en una {@link DomainTopicConfig}.
 */
public class DefaultTopicRouter implements TopicRouter {

    private final Map<String, TopicMapping> mappingByDomain;
    private final DomainTopicConfig config;

    public DefaultTopicRouter(DomainTopicConfig config) {
        this.config = Objects.requireNonNull(config);
        this.mappingByDomain = new java.util.HashMap<>();
        for (TopicMapping mapping : config.mappings()) {
            mappingByDomain.put(mapping.domain(), mapping);
        }
    }

    @Override
    public String route(Event event) {
        TopicMapping mapping = mappingByDomain.get(event.domain());
        if (mapping == null) {
            throw new IllegalStateException("No hay mapeo de topic definido para el dominio: " + event.domain());
        }
        if (config.separateErrorTopics()
                && mapping.hasErrorTopic()
                && EventType.ERROR.name().equals(event.eventType())) {
            return mapping.errorTopicName();
        }
        return mapping.topicName();
    }

    public DomainTopicConfig config() {
        return config;
    }

    public synchronized void addMapping(TopicMapping mapping) {
        mappingByDomain.put(mapping.domain(), mapping);
    }

    public synchronized void removeMapping(String domain) {
        mappingByDomain.remove(domain);
    }
}