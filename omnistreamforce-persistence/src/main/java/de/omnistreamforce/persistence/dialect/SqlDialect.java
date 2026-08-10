package de.omnistreamforce.persistence.dialect;

import de.omnistreamforce.persistence.ddl.ColumnModel;
import de.omnistreamforce.persistence.ddl.SqlType;
import de.omnistreamforce.persistence.ddl.TableModel;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Todo lo que depende del motor de base de datos vive detras de esta interfaz.
 * Anadir Oracle o MySQL es escribir una implementacion nueva, sin tocar el resto del modulo.
 */
public interface SqlDialect {

    /** Identificador corto del dialecto: {@code postgresql}, {@code oracle}, ... */
    String name();

    /** Longitud maxima de un identificador (63 en Postgres, 30 en Oracle antiguo). */
    int maxIdentifierLength();

    String quoteIdentifier(String raw);

    /** Tipo fisico para un tipo logico. */
    String sqlTypeFor(SqlType type);

    // --- Tablas de negocio ---------------------------------------------------

    String createTableIfNotExists(TableModel table);

    /** Indices de la tabla de negocio; lista vacia si el dialecto no los necesita. */
    List<String> createTableIndexes(TableModel table);

    /** Anade una columna nueva sin destruir nada (promocion de campo tras deriva de esquema). */
    String addColumnIfNotExists(String tableName, ColumnModel column);

    /** Columnas existentes de una tabla, en minusculas; vacia si la tabla no existe. */
    String selectExistingColumns();

    /**
     * INSERT que ignora silenciosamente una clave primaria ya presente, para que reintentar
     * un lote entero sea seguro (escritura at-least-once con efecto exactly-once).
     */
    String insertIgnoreConflict(String tableName, List<String> columns, String primaryKeyColumn);

    // --- Outbox --------------------------------------------------------------

    String createOutboxTable(String tableName);

    List<String> createOutboxIndexes(String tableName);

    String insertOutbox(String tableName);

    /** Reclama un lote de filas pendientes bloqueandolas y saltando las ya bloqueadas. */
    String selectPendingForUpdateSkipLocked(String tableName);

    String markPublished(String tableName);

    String deletePublished(String tableName);

    String markFailed(String tableName);

    /** Numero de filas pendientes y antiguedad en segundos de la mas vieja. */
    String outboxLagQuery(String tableName);

    String purgePublished(String tableName);

    /** Predicado de sharding para repartir agregados entre workers conservando el orden. */
    String aggregateShardPredicate(int workers, int workerIndex);

    // --- Binding -------------------------------------------------------------

    /** Enlaza un documento JSON al statement (en Postgres requiere {@code Types.OTHER}). */
    void bindJson(PreparedStatement statement, int index, String json) throws SQLException;

    /**
     * Bloqueo cooperativo para que varios procesos no creen la misma tabla a la vez;
     * {@code null} si el motor no lo soporta.
     */
    String advisoryLock();

    boolean supportsSkipLocked();
}
