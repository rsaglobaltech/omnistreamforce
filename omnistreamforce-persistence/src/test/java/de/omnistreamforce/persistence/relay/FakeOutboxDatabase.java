package de.omnistreamforce.persistence.relay;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tabla outbox simulada: sirve lotes de filas preprogramados y registra en que orden se
 * ejecutan las sentencias, para poder afirmar que el relay publica ANTES de marcar.
 */
final class FakeOutboxDatabase {

    /** Traza global de acciones: "select", "publish", "flush", "update:PUBLISHED", "commit"... */
    final List<String> actions = Collections.synchronizedList(new ArrayList<>());
    final List<String> executedSql = Collections.synchronizedList(new ArrayList<>());
    final AtomicInteger commits = new AtomicInteger();
    final AtomicInteger rollbacks = new AtomicInteger();

    private final Deque<List<Map<String, Object>>> pendingBatches = new ArrayDeque<>();

    /** Programa el siguiente lote que devolvera el SELECT de reclamo. */
    void enqueueBatch(List<Map<String, Object>> rows) {
        pendingBatches.add(rows);
    }

    static Map<String, Object> row(String id, String topic, String key, String payload, int attempts) {
        return Map.of("id", id, "aggregatetype", topic, "aggregateid", key,
                "payload", payload, "attempts", attempts, "seq", 1L);
    }

    DataSource dataSource() {
        return (DataSource) Proxy.newProxyInstance(
                FakeOutboxDatabase.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> "getConnection".equals(method.getName())
                        ? newConnection() : defaultValue(method));
    }

    private Connection newConnection() {
        return (Connection) Proxy.newProxyInstance(
                FakeOutboxDatabase.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "commit" -> {
                        commits.incrementAndGet();
                        actions.add("commit");
                        yield null;
                    }
                    case "rollback" -> {
                        rollbacks.incrementAndGet();
                        actions.add("rollback");
                        yield null;
                    }
                    case "prepareStatement" -> newStatement((String) args[0]);
                    case "createArrayOf" -> newArray();
                    default -> defaultValue(method);
                });
    }

    private Object newArray() {
        return Proxy.newProxyInstance(FakeOutboxDatabase.class.getClassLoader(),
                new Class<?>[]{java.sql.Array.class},
                (proxy, method, args) -> defaultValue(method));
    }

    private PreparedStatement newStatement(String sql) {
        String upper = sql.toUpperCase(Locale.ROOT);
        return (PreparedStatement) Proxy.newProxyInstance(
                FakeOutboxDatabase.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "executeQuery" -> {
                        executedSql.add(sql);
                        if (upper.contains("FOR UPDATE SKIP LOCKED")) {
                            actions.add("select");
                            yield resultSet(pendingBatches.isEmpty() ? List.of() : pendingBatches.poll());
                        }
                        yield resultSet(List.of());
                    }
                    case "executeUpdate" -> {
                        executedSql.add(sql);
                        if (upper.startsWith("UPDATE") && upper.contains("PUBLISHED")) {
                            actions.add("update:PUBLISHED");
                        } else if (upper.startsWith("UPDATE")) {
                            actions.add("update:FAILED");
                        } else if (upper.startsWith("DELETE")) {
                            actions.add("delete");
                        }
                        yield 0;
                    }
                    default -> defaultValue(method);
                });
    }

    private ResultSet resultSet(List<Map<String, Object>> rows) {
        AtomicInteger cursor = new AtomicInteger(-1);
        return (ResultSet) Proxy.newProxyInstance(
                FakeOutboxDatabase.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> cursor.incrementAndGet() < rows.size();
                    case "getString" -> value(rows, cursor, args, String.class);
                    case "getInt" -> {
                        Object value = value(rows, cursor, args, Integer.class);
                        yield value == null ? 0 : ((Number) value).intValue();
                    }
                    case "getLong" -> {
                        Object value = value(rows, cursor, args, Long.class);
                        yield value == null ? 0L : ((Number) value).longValue();
                    }
                    case "getDouble" -> 0.0d;
                    default -> defaultValue(method);
                });
    }

    private Object value(List<Map<String, Object>> rows, AtomicInteger cursor, Object[] args,
                         Class<?> expected) throws SQLException {
        int index = cursor.get();
        if (index < 0 || index >= rows.size()) {
            throw new SQLException("cursor fuera de rango");
        }
        Object key = args[0];
        Object value = key instanceof String name ? rows.get(index).get(name) : null;
        return expected.isInstance(value) || value instanceof Number ? value : null;
    }

    private static Object defaultValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0d;
        if (type == float.class) return 0.0f;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        if (type == int[].class) return new int[0];
        return null;
    }
}
