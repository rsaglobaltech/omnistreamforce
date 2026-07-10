package de.omnistreamforce.core;

import java.util.List;

/**
 * Especificacion de un tipo de evento concreto dentro de un dominio.
 */
public record EventSpec(
        String typeName,
        EventType eventType,
        String description,
        List<FieldDefinition> fields
) {
    public EventSpec {
        if (fields != null) {
            fields = List.copyOf(fields);
        }
    }
}