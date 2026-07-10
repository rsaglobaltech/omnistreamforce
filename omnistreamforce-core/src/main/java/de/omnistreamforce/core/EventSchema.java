package de.omnistreamforce.core;

import java.util.List;

/**
 * Definicion del esquema de eventos de un dominio.
 */
public record EventSchema(
        String domain,
        String name,
        String description,
        List<FieldDefinition> fields,
        List<EventSpec> eventTypes,
        ErrorSpec errorSpec
) {
    public EventSchema {
        if (fields != null) {
            fields = List.copyOf(fields);
        }
        if (eventTypes != null) {
            eventTypes = List.copyOf(eventTypes);
        }
    }
}