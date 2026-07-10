package de.omnistreamforce.core;

import java.util.List;

/**
 * Definicion de un campo dentro de un esquema de evento de dominio.
 */
public record FieldDefinition(
        String name,
        String type,
        boolean required,
        String fakerExpression,
        Object defaultValue,
        List<String> enumValues,
        String description
) {
    public FieldDefinition {
        if (enumValues != null) {
            enumValues = List.copyOf(enumValues);
        }
    }
}