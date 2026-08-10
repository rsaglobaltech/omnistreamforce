package de.omnistreamforce.persistence.relay;

import de.omnistreamforce.persistence.PersistenceConfig;

/**
 * Configuracion del relay del outbox.
 *
 * @param workerThreads    por defecto 1: {@code ORDER BY seq} da orden total dentro de un worker.
 *                         Con mas de uno se activa el sharding por agregado, de modo que todas
 *                         las filas de un mismo aggregateid caen siempre en el mismo worker
 * @param maxAttempts      intentos antes de marcar una fila como FAILED
 * @param deleteAfterPublish borra la fila en vez de marcarla; incompatible con CDC sobre la
 *                         misma tabla si el conector aun no la ha leido
 * @param retentionMinutes antiguedad a partir de la cual se purgan las filas ya publicadas
 * @param ownsPublisher    si el relay debe cerrar el publisher al pararse. En modo dual el
 *                         publisher es compartido con el sink y cerrarlo seria un error
 */
public record RelayConfig(
        String outboxTable,
        long pollIntervalMs,
        long maxPollIntervalMs,
        int batchSize,
        int workerThreads,
        int maxAttempts,
        long retryBackoffMs,
        boolean deleteAfterPublish,
        long retentionMinutes,
        long purgeIntervalMs,
        long lagSampleIntervalMs,
        boolean ownsPublisher,
        boolean ownsDataSource
) {

    public RelayConfig {
        outboxTable = outboxTable == null || outboxTable.isBlank()
                ? PersistenceConfig.DEFAULT_OUTBOX_TABLE : outboxTable;
        pollIntervalMs = Math.max(1, pollIntervalMs);
        maxPollIntervalMs = Math.max(pollIntervalMs, maxPollIntervalMs);
        batchSize = Math.max(1, batchSize);
        workerThreads = Math.max(1, workerThreads);
        maxAttempts = Math.max(1, maxAttempts);
        retryBackoffMs = Math.max(0, retryBackoffMs);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RelayConfig defaults() {
        return builder().build();
    }

    public static final class Builder {
        private String outboxTable = PersistenceConfig.DEFAULT_OUTBOX_TABLE;
        private long pollIntervalMs = 200;
        private long maxPollIntervalMs = 2_000;
        private int batchSize = 500;
        private int workerThreads = 1;
        private int maxAttempts = 5;
        private long retryBackoffMs = 500;
        private boolean deleteAfterPublish = false;
        private long retentionMinutes = 60;
        private long purgeIntervalMs = 60_000;
        private long lagSampleIntervalMs = 5_000;
        private boolean ownsPublisher = false;
        private boolean ownsDataSource = false;

        public Builder outboxTable(String v) { this.outboxTable = v; return this; }
        public Builder pollIntervalMs(long v) { this.pollIntervalMs = v; return this; }
        public Builder maxPollIntervalMs(long v) { this.maxPollIntervalMs = v; return this; }
        public Builder batchSize(int v) { this.batchSize = v; return this; }
        public Builder workerThreads(int v) { this.workerThreads = v; return this; }
        public Builder maxAttempts(int v) { this.maxAttempts = v; return this; }
        public Builder retryBackoffMs(long v) { this.retryBackoffMs = v; return this; }
        public Builder deleteAfterPublish(boolean v) { this.deleteAfterPublish = v; return this; }
        public Builder retentionMinutes(long v) { this.retentionMinutes = v; return this; }
        public Builder purgeIntervalMs(long v) { this.purgeIntervalMs = v; return this; }
        public Builder lagSampleIntervalMs(long v) { this.lagSampleIntervalMs = v; return this; }
        public Builder ownsPublisher(boolean v) { this.ownsPublisher = v; return this; }
        public Builder ownsDataSource(boolean v) { this.ownsDataSource = v; return this; }

        public RelayConfig build() {
            return new RelayConfig(outboxTable, pollIntervalMs, maxPollIntervalMs, batchSize,
                    workerThreads, maxAttempts, retryBackoffMs, deleteAfterPublish, retentionMinutes,
                    purgeIntervalMs, lagSampleIntervalMs, ownsPublisher, ownsDataSource);
        }
    }
}
