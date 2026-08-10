package de.omnistreamforce.persistence.relay;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.engine.EventPublisher;
import de.omnistreamforce.kafka.KafkaEventPublisher;
import de.omnistreamforce.persistence.DataSourceFactory;
import de.omnistreamforce.persistence.PersistenceException;
import de.omnistreamforce.persistence.dialect.SqlDialect;
import de.omnistreamforce.serializer.EventSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Relay del outbox: lee las filas pendientes y las publica en Kafka reutilizando el
 * {@link EventPublisher} existente.
 * <p>
 * Es la alternativa a CDC: mismo resultado, sin conector externo. Puede convivir con Debezium
 * publicando a topics con prefijo distinto para poder compararlos.
 * <p>
 * Garantia <b>at-least-once</b>: se publica y se hace {@code flush()} <em>antes</em> de marcar las
 * filas como publicadas. Si el proceso muere entre ambas cosas, las filas siguen PENDING y se
 * reenvian; el consumidor deduplica por {@code eventId}.
 */
public final class OutboxRelay implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /** Metadata donde el motor deja la clave del mensaje; el relay la reconstruye igual. */
    private static final String KEY_METADATA = "kafka.key";

    private record ClaimedRow(String id, String topic, String key, String payload, int attempts) {
    }

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final EventPublisher publisher;
    private final EventSerializer serializer;
    private final RelayConfig config;
    private final RelayStats stats = new RelayStats();
    /** Fallos acumulados del publisher: sirve para saber si el lote llego de verdad. */
    private final LongSupplier publisherFailures;

    private final AtomicBoolean running = new AtomicBoolean();
    private final List<Thread> workers = new ArrayList<>();
    private ScheduledExecutorService housekeeping;

    public OutboxRelay(DataSource dataSource, SqlDialect dialect, EventPublisher publisher,
                       EventSerializer serializer, RelayConfig config) {
        this(dataSource, dialect, publisher, serializer, config, defaultFailureCounter(publisher));
    }

    public OutboxRelay(DataSource dataSource, SqlDialect dialect, EventPublisher publisher,
                       EventSerializer serializer, RelayConfig config, LongSupplier publisherFailures) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.publisher = publisher;
        this.serializer = serializer;
        this.config = config == null ? RelayConfig.defaults() : config;
        this.publisherFailures = publisherFailures == null ? () -> 0L : publisherFailures;
    }

    /**
     * {@code flush()} garantiza que el productor vacio su buffer, pero no dice que registro
     * concreto fallo. Comparando el contador de fallos antes y despues se sabe si el lote
     * completo llego; la granularidad es de lote, no de fila.
     */
    private static LongSupplier defaultFailureCounter(EventPublisher publisher) {
        if (publisher instanceof KafkaEventPublisher kafka) {
            return () -> kafka.metrics().totalFailed();
        }
        return () -> 0L;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        for (int i = 0; i < config.workerThreads(); i++) {
            final int index = i;
            Thread worker = new Thread(() -> workerLoop(index), "osf-outbox-relay-" + i);
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }
        housekeeping = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "osf-outbox-housekeeping");
            thread.setDaemon(true);
            return thread;
        });
        housekeeping.scheduleWithFixedDelay(this::sampleLag, 0,
                config.lagSampleIntervalMs(), TimeUnit.MILLISECONDS);
        housekeeping.scheduleWithFixedDelay(this::purge, config.purgeIntervalMs(),
                config.purgeIntervalMs(), TimeUnit.MILLISECONDS);
        log.info("Relay del outbox arrancado: {} worker(s) sobre {}",
                config.workerThreads(), config.outboxTable());
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        workers.forEach(Thread::interrupt);
        for (Thread worker : workers) {
            try {
                worker.join(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        workers.clear();
        if (housekeeping != null) {
            housekeeping.shutdownNow();
        }
        if (config.ownsPublisher()) {
            publisher.close();
        }
        if (config.ownsDataSource()) {
            DataSourceFactory.close(dataSource);
        }
        log.info("Relay del outbox detenido: {}", stats);
    }

    @Override
    public void close() {
        stop();
    }

    public RelayStats stats() {
        return stats;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Procesa un lote y devuelve cuantas filas se reclamaron. Publico para poder ejercitar el
     * ciclo completo desde los tests sin depender del temporizador.
     */
    public int drainOnce() {
        return processBatch(0);
    }

    // --- ciclo del worker ----------------------------------------------------

    private void workerLoop(int workerIndex) {
        long idleBackoff = config.pollIntervalMs();
        while (running.get()) {
            try {
                int processed = processBatch(workerIndex);
                if (processed >= config.batchSize()) {
                    // el lote vino lleno: hay retraso acumulado, se repolea sin esperar
                    idleBackoff = config.pollIntervalMs();
                    continue;
                }
                if (processed == 0) {
                    idleBackoff = Math.min(idleBackoff * 2, config.maxPollIntervalMs());
                } else {
                    idleBackoff = config.pollIntervalMs();
                }
                Thread.sleep(idleBackoff);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("Error en el worker {} del relay", workerIndex, e);
                sleepQuietly(config.pollIntervalMs());
            }
        }
    }

    private int processBatch(int workerIndex) {
        stats.recordPoll();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<ClaimedRow> rows = claim(connection, workerIndex);
                if (rows.isEmpty()) {
                    connection.commit();
                    return 0;
                }
                stats.recordClaimed(rows.size());

                long failuresBefore = publisherFailures.getAsLong();
                long start = System.nanoTime();
                for (ClaimedRow row : rows) {
                    publisher.publish(toEvent(row), row.topic());
                }
                // publicar y confirmar ANTES de marcar: si el proceso muere aqui, las filas
                // siguen PENDING y se reenvian (at-least-once)
                publisher.flush();
                double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

                if (publisherFailures.getAsLong() > failuresBefore) {
                    markFailed(connection, rows, "el publisher reporto fallos en el lote");
                    connection.commit();
                    stats.recordFailed(rows.size());
                    sleepQuietly(config.retryBackoffMs());
                } else {
                    markPublished(connection, rows);
                    connection.commit();
                    stats.recordPublished(rows.size(), latencyMs);
                }
                return rows.size();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Fallo procesando un lote del outbox", e);
        }
    }

    private List<ClaimedRow> claim(Connection connection, int workerIndex) throws SQLException {
        String sql = dialect.selectPendingForUpdateSkipLocked(config.outboxTable());
        String shard = config.workerThreads() > 1
                ? dialect.aggregateShardPredicate(config.workerThreads(), workerIndex) : "";
        if (!shard.isEmpty()) {
            // el predicado de sharding va con el resto del WHERE, antes del ORDER BY
            sql = sql.replace(" ORDER BY seq", shard + " ORDER BY seq");
        }

        List<ClaimedRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, config.maxAttempts());
            statement.setInt(2, config.batchSize());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ClaimedRow(
                            rs.getString("id"),
                            rs.getString("aggregatetype"),
                            rs.getString("aggregateid"),
                            rs.getString("payload"),
                            rs.getInt("attempts")));
                }
            }
        }
        return rows;
    }

    private Event toEvent(ClaimedRow row) {
        Event event = serializer.deserialize(row.payload().getBytes(StandardCharsets.UTF_8));
        Map<String, String> metadata = new LinkedHashMap<>(
                event.metadata() == null ? Map.of() : event.metadata());
        // reconstruye exactamente la clave con la que se guardo la fila
        metadata.put(KEY_METADATA, row.key());
        return new Event(event.eventId(), event.eventType(), event.domain(), event.source(),
                event.timestamp(), event.schemaVersion(), event.payload(), metadata,
                event.traceId(), event.correlationId());
    }

    private void markPublished(Connection connection, List<ClaimedRow> rows) throws SQLException {
        String sql = config.deleteAfterPublish()
                ? dialect.deletePublished(config.outboxTable())
                : dialect.markPublished(config.outboxTable());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, idArray(connection, rows));
            statement.executeUpdate();
        }
    }

    private void markFailed(Connection connection, List<ClaimedRow> rows, String error)
            throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement(dialect.markFailed(config.outboxTable()))) {
            statement.setString(1, error);
            statement.setInt(2, config.maxAttempts());
            statement.setArray(3, idArray(connection, rows));
            statement.executeUpdate();
        }
        long exhausted = rows.stream().filter(row -> row.attempts() + 1 >= config.maxAttempts()).count();
        if (exhausted > 0) {
            stats.recordDeadLettered((int) exhausted);
            log.error("{} fila(s) del outbox agotaron los {} intentos y quedan en FAILED",
                    exhausted, config.maxAttempts());
        }
    }

    private Array idArray(Connection connection, List<ClaimedRow> rows) throws SQLException {
        return connection.createArrayOf("varchar", rows.stream().map(ClaimedRow::id).toArray());
    }

    // --- mantenimiento -------------------------------------------------------

    private void sampleLag() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(dialect.outboxLagQuery(config.outboxTable()));
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                stats.recordLag(rs.getLong(1), rs.getDouble(2));
            }
            connection.commit();
        } catch (SQLException e) {
            log.debug("No se pudo medir el retraso del outbox: {}", e.getMessage());
        }
    }

    private void purge() {
        if (config.retentionMinutes() <= 0) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(dialect.purgePublished(config.outboxTable()))) {
            statement.setLong(1, config.retentionMinutes());
            int purged = statement.executeUpdate();
            connection.commit();
            if (purged > 0) {
                stats.recordPurged(purged);
                log.debug("Purgadas {} filas publicadas del outbox", purged);
            }
        } catch (SQLException e) {
            log.debug("No se pudo purgar el outbox: {}", e.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
