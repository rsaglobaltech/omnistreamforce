package de.omnistreamforce.core;

import java.util.Map;

/**
 * Evento generico que todos los dominios deben producir.
 */
public record Event(
        String eventId,
        String eventType,
        String domain,
        String source,
        long timestamp,
        String schemaVersion,
        Map<String, Object> payload,
        Map<String, String> metadata,
        String traceId,
        String correlationId
) {
}