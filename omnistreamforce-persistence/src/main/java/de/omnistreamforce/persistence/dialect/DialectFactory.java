package de.omnistreamforce.persistence.dialect;

import java.util.Locale;

/**
 * Resuelve el dialecto a partir de la URL JDBC. Hoy solo PostgreSQL; Oracle y MySQL
 * entran anadiendo una implementacion de {@link SqlDialect} y una rama aqui.
 */
public final class DialectFactory {

    private DialectFactory() {
    }

    public static SqlDialect fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("La URL JDBC no puede estar vacia");
        }
        String url = jdbcUrl.toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:postgresql:")) {
            return new PostgresDialect();
        }
        throw new IllegalArgumentException("Motor de base de datos no soportado todavia: " + jdbcUrl);
    }
}
