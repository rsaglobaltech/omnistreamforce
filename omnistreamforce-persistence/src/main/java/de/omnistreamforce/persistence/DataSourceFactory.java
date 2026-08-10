package de.omnistreamforce.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Crea los pools de conexiones. El sink y el relay usan pools separados: el relay mantiene
 * transacciones largas con FOR UPDATE mientras espera al broker y no debe dejar sin conexiones
 * a los escritores.
 */
public final class DataSourceFactory {

    private DataSourceFactory() {
    }

    public static DataSource create(PersistenceConfig config, String poolName) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(config.poolSize());
        hikari.setMinimumIdle(Math.min(2, config.poolSize()));
        hikari.setPoolName(poolName);
        hikari.setAutoCommit(false);
        hikari.setConnectionTimeout(10_000);
        return new HikariDataSource(hikari);
    }

    /** Cierra el pool si es cerrable, ignorando el tipo concreto de DataSource. */
    public static void close(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new PersistenceException("Error cerrando el pool de conexiones", e);
            }
        }
    }
}
