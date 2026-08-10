package de.omnistreamforce.persistence.ddl;

import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.FieldDefinition;
import de.omnistreamforce.persistence.dialect.SqlDialect;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Construye el modelo de tabla de un dominio a partir de su {@link EventSchema} y emite el DDL.
 * <p>
 * Cada tabla tiene tres bloques:
 * <ol>
 *   <li><b>envelope</b>: campos comunes del {@code Event}, iguales en todos los dominios;</li>
 *   <li><b>columnas tipadas</b>: una por cada campo declarado en el esquema, indexables;</li>
 *   <li><b>overflow JSON</b>: {@code payload} completo, {@code payload_extra} con las claves que el
 *       esquema no declara (los generadores anaden muchas) y {@code metadata}.</li>
 * </ol>
 * Asi no se pierde ni un dato y la deriva de esquema queda medible con una consulta.
 */
public final class DdlGenerator {

    public static final String COL_EVENT_ID = "event_id";
    public static final String COL_PAYLOAD = "payload";
    public static final String COL_PAYLOAD_EXTRA = "payload_extra";
    public static final String COL_METADATA = "metadata";

    private final SqlDialect dialect;
    private final DdlOptions options;

    public DdlGenerator(SqlDialect dialect, DdlOptions options) {
        this.dialect = dialect;
        this.options = options == null ? DdlOptions.defaults() : options;
    }

    public DdlOptions options() {
        return options;
    }

    public TableModel tableFor(EventSchema schema) {
        String tableName = NameMapper.tableName(schema.domain(), options, dialect.maxIdentifierLength());
        NameMapper mapper = new NameMapper(dialect.maxIdentifierLength());

        List<ColumnModel> envelope = envelopeColumns();
        envelope.forEach(column -> mapper.reserve(column.name()));

        List<ColumnModel> payloadColumns = new ArrayList<>();
        Set<String> seenFields = new LinkedHashSet<>();
        for (FieldDefinition field : declaredFields(schema)) {
            if (field.name() == null || !seenFields.add(field.name())) {
                continue;
            }
            payloadColumns.add(new ColumnModel(
                    mapper.columnFor(field.name()),
                    TypeMapper.map(field, options),
                    !(options.enforceRequired() && field.required()),
                    field.name(),
                    field.enumValues()));
        }

        return new TableModel(tableName, schema.domain(), envelope, payloadColumns, overflowColumns());
    }

    /**
     * Campos declarados: los del esquema mas los que solo aparezcan en algun EventSpec concreto.
     */
    private List<FieldDefinition> declaredFields(EventSchema schema) {
        List<FieldDefinition> fields = new ArrayList<>();
        if (schema.fields() != null) {
            fields.addAll(schema.fields());
        }
        if (schema.eventTypes() != null) {
            schema.eventTypes().stream()
                    .filter(spec -> spec.fields() != null)
                    .forEach(spec -> fields.addAll(spec.fields()));
        }
        return fields;
    }

    private List<ColumnModel> envelopeColumns() {
        List<ColumnModel> envelope = new ArrayList<>();
        envelope.add(ColumnModel.envelope(COL_EVENT_ID, SqlType.TEXT, false));
        envelope.add(ColumnModel.envelope("event_type", SqlType.TEXT, false));
        envelope.add(ColumnModel.envelope("event_name", SqlType.TEXT, true));
        envelope.add(ColumnModel.envelope("domain", SqlType.TEXT, false));
        envelope.add(ColumnModel.envelope("source", SqlType.TEXT, true));
        envelope.add(ColumnModel.envelope("event_ts", SqlType.TIMESTAMPTZ, false));
        envelope.add(ColumnModel.envelope("schema_version", SqlType.TEXT, true));
        envelope.add(ColumnModel.envelope("trace_id", SqlType.TEXT, true));
        envelope.add(ColumnModel.envelope("correlation_id", SqlType.TEXT, true));
        envelope.add(ColumnModel.envelope("severity", SqlType.TEXT, true));
        envelope.add(ColumnModel.envelope("error_code", SqlType.TEXT, true));
        envelope.add(ColumnModel.envelope("error_message", SqlType.TEXT, true));
        envelope.add(ColumnModel.envelope("message_key", SqlType.TEXT, true));
        envelope.add(ColumnModel.envelope("topic", SqlType.TEXT, true));
        return envelope;
    }

    private List<ColumnModel> overflowColumns() {
        List<ColumnModel> overflow = new ArrayList<>();
        if (options.storeFullPayload()) {
            overflow.add(ColumnModel.envelope(COL_PAYLOAD, SqlType.JSON, false));
        }
        overflow.add(ColumnModel.envelope(COL_PAYLOAD_EXTRA, SqlType.JSON, true));
        overflow.add(ColumnModel.envelope(COL_METADATA, SqlType.JSON, false));
        overflow.add(ColumnModel.envelope("ingested_at", SqlType.TIMESTAMPTZ, false));
        return overflow;
    }

    public String createTable(TableModel table) {
        return dialect.createTableIfNotExists(table);
    }

    public List<String> createIndexes(TableModel table) {
        return dialect.createTableIndexes(table);
    }

    /**
     * Sentencias para poner al dia una tabla ya existente. Solo anade columnas: promover un campo
     * del payload a columna propia nunca puede destruir datos, y jamas se emite DROP COLUMN.
     */
    public List<String> alterStatements(TableModel table, Set<String> existingColumns) {
        Set<String> existing = new LinkedHashSet<>();
        existingColumns.forEach(column -> existing.add(column.toLowerCase(Locale.ROOT)));
        List<String> statements = new ArrayList<>();
        for (ColumnModel column : table.allColumns()) {
            if (!existing.contains(column.name().toLowerCase(Locale.ROOT))) {
                // una columna anadida a posteriori no puede ser NOT NULL: ya hay filas sin ella
                ColumnModel nullable = column.nullable()
                        ? column
                        : new ColumnModel(column.name(), column.type(), true,
                        column.sourceField(), column.enumValues());
                statements.add(dialect.addColumnIfNotExists(table.tableName(), nullable));
            }
        }
        return statements;
    }
}
