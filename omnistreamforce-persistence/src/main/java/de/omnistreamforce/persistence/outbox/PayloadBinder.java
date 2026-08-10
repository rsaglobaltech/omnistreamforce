package de.omnistreamforce.persistence.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.omnistreamforce.core.Event;
import de.omnistreamforce.persistence.PersistenceException;
import de.omnistreamforce.persistence.ddl.ColumnModel;
import de.omnistreamforce.persistence.ddl.DdlGenerator;
import de.omnistreamforce.persistence.ddl.PayloadSplitter;
import de.omnistreamforce.persistence.ddl.TableModel;
import de.omnistreamforce.persistence.dialect.SqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enlaza un {@link Event} a la fila de su tabla de negocio.
 * <p>
 * El tipo declarado en el esquema es una pista, no una garantia: el generador puede poner un
 * Integer donde el esquema dice double. Por eso el binding es defensivo y, pase lo que pase, el
 * valor original sobrevive en {@code payload} / {@code payload_extra}.
 */
public final class PayloadBinder {

    private static final Logger log = LoggerFactory.getLogger(PayloadBinder.class);

    private final SqlDialect dialect;
    private final ObjectMapper mapper;
    /** Evita repetir el mismo WARN de coercion en cada evento. */
    private final Set<String> warnedColumns = ConcurrentHashMap.newKeySet();

    public PayloadBinder(SqlDialect dialect, ObjectMapper mapper) {
        this.dialect = dialect;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    /**
     * Enlaza todas las columnas de la tabla en el orden de {@link TableModel#allColumns()}.
     */
    public void bind(PreparedStatement statement, TableModel table, Event event, String topic,
                     String messageKey) throws SQLException {
        int index = 1;
        for (ColumnModel column : table.envelope()) {
            bindEnvelope(statement, index++, column, event, topic, messageKey);
        }
        Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
        for (ColumnModel column : table.payloadColumns()) {
            bindPayloadValue(statement, index++, column, payload.get(column.sourceField()), table);
        }
        for (ColumnModel column : table.overflow()) {
            bindOverflow(statement, index++, column, table, event, payload);
        }
    }

    private void bindEnvelope(PreparedStatement statement, int index, ColumnModel column,
                              Event event, String topic, String messageKey) throws SQLException {
        switch (column.name()) {
            case DdlGenerator.COL_EVENT_ID -> statement.setString(index, event.eventId());
            case "event_type" -> statement.setString(index, event.eventType());
            case "event_name" -> statement.setString(index, OutboxRecordMapper.eventName(event));
            case "domain" -> statement.setString(index, event.domain());
            case "source" -> statement.setString(index, event.source());
            // siempre UTC explicito: java.sql.Timestamp aplicaria la zona de la JVM
            case "event_ts" -> statement.setObject(index,
                    OffsetDateTime.ofInstant(Instant.ofEpochMilli(event.timestamp()), ZoneOffset.UTC));
            case "schema_version" -> statement.setString(index, event.schemaVersion());
            case "trace_id" -> statement.setString(index, event.traceId());
            case "correlation_id" -> statement.setString(index, event.correlationId());
            case "severity" -> statement.setString(index, OutboxRecordMapper.metadata(event, "severity"));
            case "error_code" -> statement.setString(index, OutboxRecordMapper.metadata(event, "errorCode"));
            case "error_message" -> statement.setString(index, OutboxRecordMapper.metadata(event, "errorMessage"));
            // la misma clave que lleva la fila de outbox y que llevara el mensaje en Kafka
            case "message_key" -> statement.setString(index, messageKey);
            case "topic" -> statement.setString(index, topic);
            default -> statement.setNull(index, Types.VARCHAR);
        }
    }

    private void bindOverflow(PreparedStatement statement, int index, ColumnModel column,
                              TableModel table, Event event, Map<String, Object> payload)
            throws SQLException {
        switch (column.name()) {
            case DdlGenerator.COL_PAYLOAD -> dialect.bindJson(statement, index, toJson(payload));
            case DdlGenerator.COL_PAYLOAD_EXTRA -> {
                Map<String, Object> extra = PayloadSplitter.undeclared(table, payload);
                dialect.bindJson(statement, index, extra.isEmpty() ? null : toJson(extra));
            }
            case DdlGenerator.COL_METADATA -> dialect.bindJson(statement, index,
                    toJson(event.metadata() == null ? Map.of() : event.metadata()));
            case "ingested_at" -> statement.setObject(index, OffsetDateTime.now(ZoneOffset.UTC));
            default -> statement.setNull(index, Types.OTHER);
        }
    }

    private void bindPayloadValue(PreparedStatement statement, int index, ColumnModel column,
                                  Object value, TableModel table) throws SQLException {
        if (value == null) {
            statement.setNull(index, sqlTypeCode(column));
            return;
        }
        try {
            switch (column.type()) {
                case TEXT -> statement.setString(index, String.valueOf(value));
                case BIGINT -> statement.setLong(index, ((Number) coerceNumber(value)).longValue());
                case DOUBLE -> statement.setDouble(index, ((Number) coerceNumber(value)).doubleValue());
                case NUMERIC -> statement.setBigDecimal(index, toBigDecimal(value));
                case BOOLEAN -> statement.setBoolean(index, toBoolean(value));
                case TIMESTAMPTZ -> statement.setObject(index, toTimestamp(value));
                case JSON -> dialect.bindJson(statement, index, toJson(value));
            }
        } catch (RuntimeException e) {
            // el dato no se pierde: sigue integro en payload / payload_extra
            warnOnce(table.tableName(), column, value);
            statement.setNull(index, sqlTypeCode(column));
        }
    }

    private void warnOnce(String tableName, ColumnModel column, Object value) {
        String key = tableName + "." + column.name();
        if (warnedColumns.add(key)) {
            log.warn("Valor no convertible a {} en {} (tipo {}); la columna queda NULL y el valor"
                            + " se conserva en payload", column.type(), key, value.getClass().getSimpleName());
        }
    }

    private int sqlTypeCode(ColumnModel column) {
        return switch (column.type()) {
            case TEXT -> Types.VARCHAR;
            case BIGINT -> Types.BIGINT;
            case DOUBLE -> Types.DOUBLE;
            case NUMERIC -> Types.NUMERIC;
            case BOOLEAN -> Types.BOOLEAN;
            case TIMESTAMPTZ -> Types.TIMESTAMP_WITH_TIMEZONE;
            case JSON -> Types.OTHER;
        };
    }

    private Object coerceNumber(Object value) {
        if (value instanceof Number) {
            return value;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private OffsetDateTime toTimestamp(Object value) {
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof Number number) {
            return OffsetDateTime.ofInstant(Instant.ofEpochMilli(number.longValue()), ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    public String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new PersistenceException("No se pudo serializar a JSON el valor de la columna", e);
        }
    }
}
