package de.omnistreamforce.persistence.ddl;

import java.util.List;

/**
 * Una columna de una tabla generada.
 *
 * @param name        nombre fisico de la columna (ya normalizado por {@link NameMapper})
 * @param type        tipo logico
 * @param nullable    si admite NULL. Las columnas derivadas del payload son siempre nullable:
 *                    los dominios comparten la misma lista de campos entre todos sus tipos de
 *                    evento, asi que ningun campo de payload esta presente en todas las filas
 * @param sourceField clave de origen: nombre del campo del payload, o {@code null} si es una
 *                    columna de envelope calculada a partir del propio {@code Event}
 * @param enumValues  valores admitidos si el campo era de tipo enum, para el CHECK opcional
 */
public record ColumnModel(
        String name,
        SqlType type,
        boolean nullable,
        String sourceField,
        List<String> enumValues
) {
    public ColumnModel {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }

    public static ColumnModel envelope(String name, SqlType type, boolean nullable) {
        return new ColumnModel(name, type, nullable, null, List.of());
    }

    public boolean isPayloadColumn() {
        return sourceField != null;
    }
}
