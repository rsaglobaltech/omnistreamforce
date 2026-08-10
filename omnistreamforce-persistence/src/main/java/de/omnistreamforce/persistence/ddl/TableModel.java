package de.omnistreamforce.persistence.ddl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabla de negocio de un dominio: envelope comun del {@link de.omnistreamforce.core.Event},
 * columnas tipadas derivadas del {@link de.omnistreamforce.core.EventSchema} y columnas de
 * overflow JSON.
 *
 * @param tableName      nombre fisico de la tabla (p.ej. {@code osf_fastfood_events})
 * @param domain         dominio de origen
 * @param envelope       columnas comunes a todos los dominios
 * @param payloadColumns columnas derivadas de los campos declarados en el esquema
 * @param overflow       columnas JSON de payload completo / claves no declaradas / metadata
 */
public record TableModel(
        String tableName,
        String domain,
        List<ColumnModel> envelope,
        List<ColumnModel> payloadColumns,
        List<ColumnModel> overflow
) {
    public TableModel {
        envelope = List.copyOf(envelope);
        payloadColumns = List.copyOf(payloadColumns);
        overflow = List.copyOf(overflow);
    }

    /** Todas las columnas en el orden en que se emiten en el CREATE TABLE. */
    public List<ColumnModel> allColumns() {
        List<ColumnModel> all = new ArrayList<>(envelope.size() + payloadColumns.size() + overflow.size());
        all.addAll(envelope);
        all.addAll(payloadColumns);
        all.addAll(overflow);
        return all;
    }

    public List<String> columnNames() {
        return allColumns().stream().map(ColumnModel::name).toList();
    }

    /** Campo del payload -> columna que lo almacena. Solo campos declarados en el esquema. */
    public Map<String, ColumnModel> columnsBySourceField() {
        Map<String, ColumnModel> bySource = new LinkedHashMap<>();
        for (ColumnModel column : payloadColumns) {
            bySource.put(column.sourceField(), column);
        }
        return bySource;
    }
}
