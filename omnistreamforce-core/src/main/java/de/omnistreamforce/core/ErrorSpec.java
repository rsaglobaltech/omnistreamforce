package de.omnistreamforce.core;

import java.util.List;

/**
 * Especificacion de los errores que puede generar un dominio.
 */
public record ErrorSpec(
        List<String> errorTypes,
        double errorRate,
        List<String> errorFields
) {
    public ErrorSpec {
        if (errorTypes != null) {
            errorTypes = List.copyOf(errorTypes);
        }
        if (errorFields != null) {
            errorFields = List.copyOf(errorFields);
        }
    }
}