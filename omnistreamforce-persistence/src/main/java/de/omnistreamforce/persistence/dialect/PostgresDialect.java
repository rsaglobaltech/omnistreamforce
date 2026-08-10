package de.omnistreamforce.persistence.dialect;

import de.omnistreamforce.persistence.ddl.ColumnModel;
import de.omnistreamforce.persistence.ddl.SqlType;
import de.omnistreamforce.persistence.ddl.TableModel;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialecto PostgreSQL: jsonb, ON CONFLICT DO NOTHING, FOR UPDATE SKIP LOCKED y advisory locks.
 */
public final class PostgresDialect implements SqlDialect {

    /** Postgres trunca identificadores a 63 bytes (NAMEDATALEN - 1). */
    private static final int MAX_IDENTIFIER_LENGTH = 63;

    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public int maxIdentifierLength() {
        return MAX_IDENTIFIER_LENGTH;
    }

    @Override
    public String quoteIdentifier(String raw) {
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String sqlTypeFor(SqlType type) {
        return switch (type) {
            case TEXT -> "TEXT";
            case BIGINT -> "BIGINT";
            case DOUBLE -> "DOUBLE PRECISION";
            case NUMERIC -> "NUMERIC(18,4)";
            case JSON -> "JSONB";
            case TIMESTAMPTZ -> "TIMESTAMPTZ";
            case BOOLEAN -> "BOOLEAN";
        };
    }

    @Override
    public String createTableIfNotExists(TableModel table) {
        String columns = table.allColumns().stream()
                .map(this::columnDefinition)
                .collect(Collectors.joining(",\n    "));
        return "CREATE TABLE IF NOT EXISTS " + quoteIdentifier(table.tableName()) + " (\n    "
                + columns + ",\n    CONSTRAINT " + quoteIdentifier("pk_" + table.tableName())
                + " PRIMARY KEY (" + quoteIdentifier("event_id") + ")\n)";
    }

    private String columnDefinition(ColumnModel column) {
        return quoteIdentifier(column.name()) + " " + sqlTypeFor(column.type())
                + (column.nullable() ? "" : " NOT NULL");
    }

    @Override
    public List<String> createTableIndexes(TableModel table) {
        String t = quoteIdentifier(table.tableName());
        List<String> indexes = new ArrayList<>();
        indexes.add(index(table, "ts", "(" + quoteIdentifier("event_ts") + " DESC)", t));
        indexes.add(index(table, "type", "(" + quoteIdentifier("event_type") + ")", t));
        indexes.add(index(table, "name", "(" + quoteIdentifier("event_name") + ")", t));
        indexes.add(index(table, "key", "(" + quoteIdentifier("message_key") + ")", t));
        indexes.add("CREATE INDEX IF NOT EXISTS "
                + quoteIdentifier(indexName(table.tableName(), "payload")) + " ON " + t
                + " USING gin (" + quoteIdentifier("payload") + " jsonb_path_ops)");
        return indexes;
    }

    private String index(TableModel table, String suffix, String columns, String quotedTable) {
        return "CREATE INDEX IF NOT EXISTS " + quoteIdentifier(indexName(table.tableName(), suffix))
                + " ON " + quotedTable + " " + columns;
    }

    /** El nombre del indice tambien esta sujeto al limite de 63 caracteres. */
    private String indexName(String tableName, String suffix) {
        String name = "ix_" + tableName + "_" + suffix;
        if (name.length() <= MAX_IDENTIFIER_LENGTH) {
            return name;
        }
        int room = MAX_IDENTIFIER_LENGTH - suffix.length() - 4;
        return "ix_" + tableName.substring(0, Math.max(1, room)) + "_" + suffix;
    }

    @Override
    public String addColumnIfNotExists(String tableName, ColumnModel column) {
        return "ALTER TABLE " + quoteIdentifier(tableName)
                + " ADD COLUMN IF NOT EXISTS " + columnDefinition(column);
    }

    @Override
    public String selectExistingColumns() {
        return "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = ?";
    }

    @Override
    public String insertIgnoreConflict(String tableName, List<String> columns, String primaryKeyColumn) {
        String cols = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + quoteIdentifier(tableName) + " (" + cols + ") VALUES (" + placeholders + ")"
                + " ON CONFLICT (" + quoteIdentifier(primaryKeyColumn) + ") DO NOTHING";
    }

    // --- Outbox --------------------------------------------------------------

    @Override
    public String createOutboxTable(String tableName) {
        String t = quoteIdentifier(tableName);
        return "CREATE TABLE IF NOT EXISTS " + t + " (\n"
                // columnas que el SMT EventRouter de Debezium espera por defecto
                + "    id            VARCHAR(64)  NOT NULL,\n"
                + "    aggregatetype VARCHAR(255) NOT NULL,\n"
                + "    aggregateid   VARCHAR(256) NOT NULL,\n"
                + "    type          VARCHAR(128) NOT NULL,\n"
                + "    payload       JSONB        NOT NULL,\n"
                // columnas propias del relay; Debezium las ignora salvo declararlas en el conector
                + "    seq           BIGSERIAL    NOT NULL,\n"
                + "    domain        VARCHAR(64)  NOT NULL,\n"
                + "    trace_id      VARCHAR(64),\n"
                + "    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',\n"
                + "    attempts      INT          NOT NULL DEFAULT 0,\n"
                + "    last_error    TEXT,\n"
                + "    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),\n"
                + "    published_at  TIMESTAMPTZ,\n"
                + "    CONSTRAINT " + quoteIdentifier("pk_" + tableName) + " PRIMARY KEY (id)\n"
                + ")";
    }

    @Override
    public List<String> createOutboxIndexes(String tableName) {
        String t = quoteIdentifier(tableName);
        return List.of(
                // indice PARCIAL: se mantiene diminuto aunque la tabla acumule millones de filas
                "CREATE INDEX IF NOT EXISTS " + quoteIdentifier(indexName(tableName, "pending"))
                        + " ON " + t + " (seq) WHERE status = 'PENDING'",
                "CREATE INDEX IF NOT EXISTS " + quoteIdentifier(indexName(tableName, "agg"))
                        + " ON " + t + " (aggregateid, seq)",
                "CREATE INDEX IF NOT EXISTS " + quoteIdentifier(indexName(tableName, "created"))
                        + " ON " + t + " (created_at)",
                // el ciclo INSERT -> UPDATE -> DELETE genera mucho bloat sin esto
                "ALTER TABLE " + t + " SET (fillfactor = 70, autovacuum_vacuum_scale_factor = 0.02)"
        );
    }

    @Override
    public String insertOutbox(String tableName) {
        return "INSERT INTO " + quoteIdentifier(tableName)
                + " (id, aggregatetype, aggregateid, type, payload, domain, trace_id)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
    }

    @Override
    public String selectPendingForUpdateSkipLocked(String tableName) {
        return "SELECT id, aggregatetype, aggregateid, type, payload, attempts, seq"
                + " FROM " + quoteIdentifier(tableName)
                + " WHERE status = 'PENDING' AND attempts < ?"
                + " ORDER BY seq LIMIT ? FOR UPDATE SKIP LOCKED";
    }

    @Override
    public String markPublished(String tableName) {
        return "UPDATE " + quoteIdentifier(tableName)
                + " SET status = 'PUBLISHED', published_at = now(), attempts = attempts + 1"
                + " WHERE id = ANY (?)";
    }

    @Override
    public String deletePublished(String tableName) {
        return "DELETE FROM " + quoteIdentifier(tableName) + " WHERE id = ANY (?)";
    }

    @Override
    public String markFailed(String tableName) {
        return "UPDATE " + quoteIdentifier(tableName)
                + " SET attempts = attempts + 1, last_error = ?,"
                + " status = CASE WHEN attempts + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END"
                + " WHERE id = ANY (?)";
    }

    @Override
    public String outboxLagQuery(String tableName) {
        return "SELECT count(*), COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0)"
                + " FROM " + quoteIdentifier(tableName) + " WHERE status = 'PENDING'";
    }

    @Override
    public String purgePublished(String tableName) {
        return "DELETE FROM " + quoteIdentifier(tableName)
                + " WHERE status = 'PUBLISHED' AND published_at < now() - make_interval(mins => ?)";
    }

    @Override
    public String aggregateShardPredicate(int workers, int workerIndex) {
        if (workers <= 1) {
            return "";
        }
        return " AND (abs(hashtext(aggregateid)) % " + workers + ") = " + workerIndex;
    }

    @Override
    public void bindJson(PreparedStatement statement, int index, String json) throws SQLException {
        if (json == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, json, Types.OTHER);
        }
    }

    @Override
    public String advisoryLock() {
        return "SELECT pg_advisory_xact_lock(hashtext(?))";
    }

    @Override
    public boolean supportsSkipLocked() {
        return true;
    }
}
