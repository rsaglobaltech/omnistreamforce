package de.omnistreamforce.persistence.outbox;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DataSource de mentira para probar el sink sin base de datos: registra commits, rollbacks y
 * tamanos de lote, y permite inyectar fallos.
 * <p>
 * Se implementa con proxies dinamicos porque las interfaces JDBC tienen decenas de metodos y
 * escribirlas a mano solo anadiria ruido. Sigue el espiritu del RecordingPublisher del core.
 */
final class RecordingDatabase {

    final AtomicInteger commits = new AtomicInteger();
    final AtomicInteger ddlCommits = new AtomicInteger();
    final AtomicInteger rollbacks = new AtomicInteger();
    final AtomicInteger connectionsOpened = new AtomicInteger();
    final AtomicInteger executeBatchCalls = new AtomicInteger();
    final AtomicInteger rowsAdded = new AtomicInteger();
    final List<Integer> batchSizes = Collections.synchronizedList(new ArrayList<>());
    final List<String> preparedSql = Collections.synchronizedList(new ArrayList<>());

    /** Si esta activo, todo executeBatch de INSERT falla. */
    private final AtomicBoolean failInserts = new AtomicBoolean();
    /** Numero de lotes que deben fallar antes de empezar a funcionar. */
    private final AtomicInteger failFirstBatches = new AtomicInteger();
    /** Falla solo el INSERT del outbox, para comprobar la atomicidad negocio+outbox. */
    private final AtomicBoolean failOutboxOnly = new AtomicBoolean();

    void failAllInserts(boolean fail) {
        failInserts.set(fail);
    }

    void failFirstBatches(int batches) {
        failFirstBatches.set(batches);
    }

    void failOutboxInsertsOnly(boolean fail) {
        failOutboxOnly.set(fail);
    }

    DataSource dataSource() {
        return (DataSource) Proxy.newProxyInstance(
                RecordingDatabase.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        connectionsOpened.incrementAndGet();
                        return newConnection();
                    }
                    return defaultValue(method);
                });
    }

    private Connection newConnection() {
        return (Connection) Proxy.newProxyInstance(
                RecordingDatabase.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionHandler());
    }

    /**
     * Solo cuentan los commits/rollbacks de conexiones que han escrito datos: el ejecutor de DDL
     * usa sus propias transacciones para crear tablas y no deben mezclarse con las del sink.
     */
    private final class ConnectionHandler implements InvocationHandler {

        private boolean wroteData;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "commit" -> {
                    if (wroteData) {
                        commits.incrementAndGet();
                    } else {
                        ddlCommits.incrementAndGet();
                    }
                }
                case "rollback" -> {
                    if (wroteData) {
                        rollbacks.incrementAndGet();
                    }
                }
                case "prepareStatement" -> {
                    String sql = (String) args[0];
                    preparedSql.add(sql);
                    if (sql.toUpperCase(java.util.Locale.ROOT).startsWith("INSERT")) {
                        wroteData = true;
                    }
                    return newPreparedStatement(sql);
                }
                case "createStatement" -> {
                    return newStatement();
                }
                default -> {
                    return defaultValue(method);
                }
            }
            return defaultValue(method);
        }
    }

    private Statement newStatement() {
        return (Statement) Proxy.newProxyInstance(
                RecordingDatabase.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> defaultValue(method));
    }

    private PreparedStatement newPreparedStatement(String sql) {
        boolean insert = sql.toUpperCase(java.util.Locale.ROOT).startsWith("INSERT");
        boolean outbox = sql.contains("aggregatetype");
        AtomicInteger pendingRows = new AtomicInteger();

        return (PreparedStatement) Proxy.newProxyInstance(
                RecordingDatabase.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "addBatch" -> {
                        pendingRows.incrementAndGet();
                        if (insert) {
                            rowsAdded.incrementAndGet();
                        }
                        yield null;
                    }
                    case "executeBatch" -> {
                        int rows = pendingRows.getAndSet(0);
                        if (insert) {
                            executeBatchCalls.incrementAndGet();
                            batchSizes.add(rows);
                            if (failInserts.get()
                                    || failFirstBatches.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0
                                    || (outbox && failOutboxOnly.get())) {
                                throw new SQLException("fallo inyectado en executeBatch");
                            }
                        }
                        yield new int[rows];
                    }
                    case "executeQuery" -> emptyResultSet();
                    default -> defaultValue(method);
                });
    }

    private ResultSet emptyResultSet() {
        return (ResultSet) Proxy.newProxyInstance(
                RecordingDatabase.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> defaultValue(method));
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == int[].class) {
            return new int[0];
        }
        return null;
    }
}
