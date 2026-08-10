package de.omnistreamforce.persistence.ddl;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Convierte nombres de campo del dominio en identificadores validos de base de datos.
 * <p>
 * Una instancia por tabla: recuerda los nombres ya emitidos para resolver colisiones,
 * que aparecen tanto al truncar por longitud como al chocar con el envelope.
 */
public final class NameMapper {

    /**
     * Columnas del envelope. Un campo de payload que se llame igual se renombra con prefijo
     * {@code f_} para no pisar el valor calculado a partir del propio Event.
     */
    public static final Set<String> RESERVED = Set.of(
            "event_id", "event_type", "event_name", "domain", "source", "event_ts",
            "schema_version", "trace_id", "correlation_id", "severity", "error_code",
            "error_message", "message_key", "topic",
            "payload", "payload_extra", "metadata", "ingested_at");

    private static final String COLLISION_PREFIX = "f_";

    private final int maxLength;
    private final Set<String> used = new HashSet<>();

    public NameMapper(int maxLength) {
        this.maxLength = maxLength < 8 ? 8 : maxLength;
    }

    /** Nombre de tabla para un dominio: {@code osf_} + dominio + {@code _events}. */
    public static String tableName(String domain, DdlOptions options, int maxLength) {
        String base = options.tablePrefix() + toSnakeCase(domain) + "_events";
        return base.length() <= maxLength ? base : base.substring(0, maxLength);
    }

    /** Reserva el nombre de una columna de envelope para que ningun campo de payload lo pise. */
    public void reserve(String columnName) {
        used.add(columnName);
    }

    /**
     * Nombre de columna para un campo del payload: snake_case, sin colisionar con el envelope
     * ni con otra columna ya emitida, y dentro del limite de longitud del motor.
     */
    public String columnFor(String fieldName) {
        String candidate = toSnakeCase(fieldName);
        if (candidate.isEmpty()) {
            candidate = "field";
        }
        if (RESERVED.contains(candidate)) {
            candidate = COLLISION_PREFIX + candidate;
        }
        candidate = truncate(candidate);
        String unique = candidate;
        int suffix = 2;
        while (!used.add(unique)) {
            String tail = "_" + suffix++;
            unique = truncate(candidate, maxLength - tail.length()) + tail;
        }
        return unique;
    }

    private String truncate(String value) {
        return truncate(value, maxLength);
    }

    private String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, Math.max(1, limit));
    }

    /** camelCase / PascalCase / kebab-case -> snake_case en minusculas. */
    public static String toSnakeCase(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 8);
        char[] chars = raw.trim().toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isUpperCase(c)) {
                boolean startOfWord = i > 0
                        && (Character.isLowerCase(chars[i - 1]) || Character.isDigit(chars[i - 1])
                        || (i + 1 < chars.length && Character.isLowerCase(chars[i + 1])));
                if (startOfWord && out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                    out.append('_');
                }
                out.append(Character.toLowerCase(c));
            } else if (Character.isLetterOrDigit(c)) {
                out.append(Character.toLowerCase(c));
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
        }
        String result = out.toString();
        while (result.endsWith("_")) {
            result = result.substring(0, result.length() - 1);
        }
        // un identificador no puede empezar por digito
        if (!result.isEmpty() && Character.isDigit(result.charAt(0))) {
            result = "f_" + result;
        }
        return result.toLowerCase(Locale.ROOT);
    }
}
