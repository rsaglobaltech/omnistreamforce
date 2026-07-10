package de.omnistreamforce.domain;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.Severity;
import de.omnistreamforce.util.RandomUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base abstracta para {@link DomainGenerator} que aporta utilidades comunes:
 * generacion de ids de traza, timestamps y construccion del {@link Event}.
 */
public abstract class AbstractDomainGenerator implements DomainGenerator {

    public static final String SOURCE = "omnistreamforce";

    protected String newEventId() {
        return RandomUtils.uuid();
    }

    protected String newTraceId() {
        return RandomUtils.uuid();
    }

    protected String newCorrelationId() {
        return RandomUtils.uuid();
    }

    protected long now() {
        return Instant.now().toEpochMilli();
    }

    protected Event buildEvent(String eventType, String domain, Map<String, Object> payload,
                              Map<String, String> metadata) {
        return new Event(
                newEventId(),
                eventType,
                domain,
                SOURCE,
                now(),
                "1.0",
                payload,
                metadata,
                newTraceId(),
                newCorrelationId()
        );
    }

    protected Event buildNormalEvent(String domain, String eventName, Map<String, Object> payload) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("eventName", eventName);
        return buildEvent("NORMAL", domain, payload, metadata);
    }

    protected Event buildErrorEvent(String domain, String errorType, String errorMessage,
                                    Severity severity, Map<String, Object> payload) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("errorType", errorType);
        metadata.put("errorCode", String.valueOf(errorType.hashCode() & 0xffff));
        metadata.put("errorMessage", errorMessage);
        metadata.put("severity", severity.name());
        return buildEvent("ERROR", domain, payload, metadata);
    }

    protected Map<String, Object> payload(Object... kvPairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            map.put((String) kvPairs[i], kvPairs[i + 1]);
        }
        return map;
    }
}