package de.omnistreamforce.persistence.ddl;

import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.persistence.PersistenceException;
import de.omnistreamforce.persistence.dialect.SqlDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crea y mantiene al dia las tablas de negocio y la tabla outbox.
 * <p>
 * Cachea las tablas ya preparadas y hace el trabajo real bajo un bloqueo cooperativo, porque
 * varios hilos de escritura (o varios procesos) pueden encontrarse con la misma tabla inexistente
 * a la vez, por ejemplo al anadir un dominio en caliente.
 */
public final class DdlExecutor {

    private static final Logger log = LoggerFactory.getLogger(DdlExecutor.class);

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final DdlGenerator generator;
    private final Map<String, TableModel> preparedTables = new ConcurrentHashMap<>();
    private final Set<String> preparedOutbox = ConcurrentHashMap.newKeySet();

    public DdlExecutor(DataSource dataSource, SqlDialect dialect, DdlGenerator generator) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.generator = generator;
    }

    /**
     * Garantiza que existe la tabla del dominio y que tiene todas las columnas del esquema.
     * Idempotente y seguro entre hilos y entre procesos.
     */
    public TableModel ensureTable(EventSchema schema) {
        return preparedTables.computeIfAbsent(schema.domain(), domain -> {
            TableModel table = generator.tableFor(schema);
            try (Connection connection = dataSource.getConnection()) {
                boolean autoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try {
                    lock(connection, table.tableName());
                    execute(connection, generator.createTable(table));
                    for (String index : generator.createIndexes(table)) {
                        execute(connection, index);
                    }
                    List<String> alters = generator.alterStatements(table, existingColumns(connection, table.tableName()));
                    for (String alter : alters) {
                        log.info("Deriva de esquema en {}: {}", table.tableName(), alter);
                        execute(connection, alter);
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            } catch (SQLException e) {
                throw new PersistenceException("No se pudo preparar la tabla del dominio " + domain, e);
            }
            log.info("Tabla lista para el dominio {}: {} ({} columnas)",
                    domain, table.tableName(), table.allColumns().size());
            return table;
        });
    }

    /** Garantiza que existe la tabla outbox con sus indices. */
    public void ensureOutboxTable(String outboxTable) {
        if (!preparedOutbox.add(outboxTable)) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                lock(connection, outboxTable);
                execute(connection, dialect.createOutboxTable(outboxTable));
                for (String index : dialect.createOutboxIndexes(outboxTable)) {
                    execute(connection, index);
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                preparedOutbox.remove(outboxTable);
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new PersistenceException("No se pudo preparar la tabla outbox " + outboxTable, e);
        }
    }

    private void lock(Connection connection, String key) throws SQLException {
        String lockSql = dialect.advisoryLock();
        if (lockSql == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(lockSql)) {
            statement.setString(1, key);
            statement.execute();
        }
    }

    private Set<String> existingColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(dialect.selectExistingColumns())) {
            statement.setString(1, tableName);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString(1));
                }
            }
        }
        return columns;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
