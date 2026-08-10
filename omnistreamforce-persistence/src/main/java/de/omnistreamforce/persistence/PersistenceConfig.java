package de.omnistreamforce.persistence;

import de.omnistreamforce.persistence.ddl.DdlOptions;
import de.omnistreamforce.persistence.outbox.BackpressurePolicy;

/**
 * Configuracion del sink de base de datos.
 * <p>
 * Es un record con builder y lectura desde entorno; cuando exista el sistema de configuracion
 * YAML del proyecto, bastara con mapearlo a este record.
 *
 * @param batchSize        eventos por transaccion; es la palanca que hace viable el sink
 * @param lingerMs         espera maxima antes de escribir un lote incompleto
 * @param writerThreads    hilos de escritura (plataforma, no virtuales: JDBC bloquea)
 * @param queueCapacity    profundidad de la cola entre el motor y los escritores
 * @param maxRetries       reintentos de un lote completo ante fallo de base de datos
 * @param unhealthyThreshold lotes fallidos consecutivos antes de abrir el circuito
 * @param writeBusinessTable si es false solo se escribe la tabla outbox
 */
public record PersistenceConfig(
        String jdbcUrl,
        String username,
        String password,
        String outboxTable,
        int poolSize,
        int batchSize,
        long lingerMs,
        int writerThreads,
        int queueCapacity,
        BackpressurePolicy backpressure,
        int maxRetries,
        long retryBackoffMs,
        long maxRetryBackoffMs,
        int unhealthyThreshold,
        long circuitResetMs,
        long drainTimeoutMs,
        boolean writeBusinessTable,
        DdlOptions ddlOptions
) {

    public static final String DEFAULT_OUTBOX_TABLE = "osf_outbox";

    public PersistenceConfig {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl es obligatorio");
        }
        outboxTable = outboxTable == null || outboxTable.isBlank() ? DEFAULT_OUTBOX_TABLE : outboxTable;
        batchSize = Math.max(1, batchSize);
        lingerMs = Math.max(0, lingerMs);
        writerThreads = Math.max(1, writerThreads);
        queueCapacity = Math.max(batchSize, queueCapacity);
        backpressure = backpressure == null ? BackpressurePolicy.BLOCK : backpressure;
        maxRetries = Math.max(0, maxRetries);
        unhealthyThreshold = Math.max(1, unhealthyThreshold);
        ddlOptions = ddlOptions == null ? DdlOptions.defaults() : ddlOptions;
        // el pool tiene que dar al menos una conexion por escritor, o los writers se hacen cola entre si
        poolSize = Math.max(writerThreads + 1, poolSize);
    }

    public static Builder builder(String jdbcUrl) {
        return new Builder(jdbcUrl);
    }

    /**
     * Configuracion desde variables de entorno: OSF_DB_URL, OSF_DB_USER, OSF_DB_PASSWORD,
     * OSF_DB_OUTBOX_TABLE, OSF_DB_BATCH_SIZE, OSF_DB_LINGER_MS, OSF_DB_WRITER_THREADS,
     * OSF_DB_QUEUE_CAPACITY, OSF_DB_BACKPRESSURE.
     */
    public static PersistenceConfig fromEnv() {
        return fromEnv(System::getenv);
    }

    static PersistenceConfig fromEnv(java.util.function.Function<String, String> env) {
        Builder builder = builder(env.apply("OSF_DB_URL"))
                .username(env.apply("OSF_DB_USER"))
                .password(env.apply("OSF_DB_PASSWORD"));
        applyIfPresent(env.apply("OSF_DB_OUTBOX_TABLE"), builder::outboxTable);
        applyIntIfPresent(env.apply("OSF_DB_BATCH_SIZE"), builder::batchSize);
        applyIntIfPresent(env.apply("OSF_DB_LINGER_MS"), value -> builder.lingerMs(value));
        applyIntIfPresent(env.apply("OSF_DB_WRITER_THREADS"), builder::writerThreads);
        applyIntIfPresent(env.apply("OSF_DB_QUEUE_CAPACITY"), builder::queueCapacity);
        applyIfPresent(env.apply("OSF_DB_BACKPRESSURE"),
                value -> builder.backpressure(BackpressurePolicy.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT))));
        return builder.build();
    }

    private static void applyIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private static void applyIntIfPresent(String value, java.util.function.IntConsumer setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(Integer.parseInt(value.trim()));
        }
    }

    public static final class Builder {
        private final String jdbcUrl;
        private String username;
        private String password;
        private String outboxTable = DEFAULT_OUTBOX_TABLE;
        private int poolSize = 0;
        private int batchSize = 100;
        private long lingerMs = 20;
        private int writerThreads = Math.min(4, Runtime.getRuntime().availableProcessors());
        private int queueCapacity = 10_000;
        private BackpressurePolicy backpressure = BackpressurePolicy.BLOCK;
        private int maxRetries = 3;
        private long retryBackoffMs = 200;
        private long maxRetryBackoffMs = 5_000;
        private int unhealthyThreshold = 3;
        private long circuitResetMs = 5_000;
        private long drainTimeoutMs = 30_000;
        private boolean writeBusinessTable = true;
        private DdlOptions ddlOptions = DdlOptions.defaults();

        private Builder(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public Builder username(String v) { this.username = v; return this; }
        public Builder password(String v) { this.password = v; return this; }
        public Builder outboxTable(String v) { this.outboxTable = v; return this; }
        public Builder poolSize(int v) { this.poolSize = v; return this; }
        public Builder batchSize(int v) { this.batchSize = v; return this; }
        public Builder lingerMs(long v) { this.lingerMs = v; return this; }
        public Builder writerThreads(int v) { this.writerThreads = v; return this; }
        public Builder queueCapacity(int v) { this.queueCapacity = v; return this; }
        public Builder backpressure(BackpressurePolicy v) { this.backpressure = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder retryBackoffMs(long v) { this.retryBackoffMs = v; return this; }
        public Builder maxRetryBackoffMs(long v) { this.maxRetryBackoffMs = v; return this; }
        public Builder unhealthyThreshold(int v) { this.unhealthyThreshold = v; return this; }
        public Builder circuitResetMs(long v) { this.circuitResetMs = v; return this; }
        public Builder drainTimeoutMs(long v) { this.drainTimeoutMs = v; return this; }
        public Builder writeBusinessTable(boolean v) { this.writeBusinessTable = v; return this; }
        public Builder ddlOptions(DdlOptions v) { this.ddlOptions = v; return this; }

        public PersistenceConfig build() {
            return new PersistenceConfig(jdbcUrl, username, password, outboxTable, poolSize, batchSize,
                    lingerMs, writerThreads, queueCapacity, backpressure, maxRetries, retryBackoffMs,
                    maxRetryBackoffMs, unhealthyThreshold, circuitResetMs, drainTimeoutMs,
                    writeBusinessTable, ddlOptions);
        }
    }
}
