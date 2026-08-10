package de.omnistreamforce.persistence.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.engine.EventPublisher;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.persistence.PersistenceConfig;
import de.omnistreamforce.persistence.PersistenceException;
import de.omnistreamforce.persistence.PersistenceUnavailableException;
import de.omnistreamforce.persistence.ddl.DdlExecutor;
import de.omnistreamforce.persistence.ddl.DdlGenerator;
import de.omnistreamforce.persistence.ddl.TableModel;
import de.omnistreamforce.persistence.dialect.SqlDialect;
import de.omnistreamforce.persistence.metrics.PersistenceMetrics;
import de.omnistreamforce.serializer.EventSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Sink de base de datos con patron Outbox: cada evento se materializa como fila de negocio y como
 * fila de outbox <b>en la misma transaccion</b>, de modo que nunca existe una sin la otra.
 * <p>
 * {@code publish()} no toca la base de datos: encola. El motor de generacion trabaja con ticks de
 * 100 ms en un hilo por dominio, asi que un INSERT sincrono por evento consumiria el presupuesto
 * del tick y el ritmo real caeria en silencio. Los escritores agrupan en lotes (una transaccion
 * por lote) igual que hace Kafka con {@code batch.size} y {@code linger.ms}.
 * <p>
 * Recibe llamadas concurrentes: {@code MultiDomainEngine} comparte una sola instancia de publisher
 * entre todos los dominios activos.
 */
public final class JdbcOutboxPublisher implements EventPublisher, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JdbcOutboxPublisher.class);

    private record PendingWrite(Event event, String topic) {
    }

    /** Evento con su fila de outbox ya calculada, para no resolver la clave dos veces. */
    private record PreparedWrite(PendingWrite write, OutboxRecord record) {
    }

    private final DataSource dataSource;
    private final SqlDialect dialect;
    private final PersistenceConfig config;
    private final DdlExecutor ddlExecutor;
    private final Function<String, EventSchema> schemaCatalog;
    private final OutboxRecordMapper recordMapper;
    private final PayloadBinder binder;
    private final PersistenceMetrics metrics = new PersistenceMetrics();

    private final BlockingQueue<PendingWrite> queue;
    private final List<Thread> writers = new ArrayList<>();
    private final Map<String, String> insertSqlCache = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger inFlightBatches = new AtomicInteger();
    private final AtomicInteger consecutiveBatchFailures = new AtomicInteger();
    private volatile long circuitOpenUntilMs;

    public JdbcOutboxPublisher(DataSource dataSource,
                               SqlDialect dialect,
                               PersistenceConfig config,
                               EventSerializer serializer,
                               Function<String, EventSchema> schemaCatalog) {
        this(dataSource, dialect, config, serializer, schemaCatalog, KeyStrategy.RANDOM, null);
    }

    /**
     * @param keyStrategy y {@code keyField} deben coincidir con los del publisher de Kafka para
     *                    que un evento tenga la misma clave salga por donde salga
     */
    public JdbcOutboxPublisher(DataSource dataSource,
                               SqlDialect dialect,
                               PersistenceConfig config,
                               EventSerializer serializer,
                               Function<String, EventSchema> schemaCatalog,
                               KeyStrategy keyStrategy,
                               String keyField) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.config = config;
        this.schemaCatalog = schemaCatalog;
        this.recordMapper = new OutboxRecordMapper(serializer, keyStrategy, keyField);
        ObjectMapper objectMapper = new ObjectMapper();
        this.binder = new PayloadBinder(dialect, objectMapper);
        this.ddlExecutor = new DdlExecutor(dataSource, dialect,
                new DdlGenerator(dialect, config.ddlOptions()));
        this.queue = new ArrayBlockingQueue<>(config.queueCapacity());

        metrics.bindQueueDepth(queue::size);
        ddlExecutor.ensureOutboxTable(config.outboxTable());
        startWriters();
    }

    private void startWriters() {
        for (int i = 0; i < config.writerThreads(); i++) {
            // hilos de plataforma a proposito: JDBC hace I/O bloqueante y algunos drivers
            // inmovilizarian el carrier de un hilo virtual
            Thread writer = new Thread(this::writeLoop, "osf-outbox-writer-" + i);
            writer.setDaemon(true);
            writer.start();
            writers.add(writer);
        }
    }

    // --- API de EventPublisher ----------------------------------------------

    @Override
    public void publish(Event event, String topic) {
        if (closed.get()) {
            throw new PersistenceException("El publisher ya esta cerrado");
        }
        ensureCircuitClosed();
        enqueue(new PendingWrite(event, topic));
        metrics.recordEnqueued(topic);
    }

    @Override
    public void publishBatch(List<Event> events, String topic) {
        for (Event event : events) {
            publish(event, topic);
        }
    }

    /** Espera a que todo lo encolado este confirmado en base de datos. */
    @Override
    public void flush() {
        long deadline = System.currentTimeMillis() + config.drainTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (queue.isEmpty() && inFlightBatches.get() == 0) {
                return;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("flush() agoto el tiempo de espera con {} eventos en cola", queue.size());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // se sigue drenando lo ya encolado: el motor puede parar con un tick a medias
        flush();
        running.set(false);
        writers.forEach(Thread::interrupt);
        for (Thread writer : writers) {
            try {
                writer.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Sink de base de datos cerrado: {} eventos escritos, {} fallidos, {} descartados",
                metrics.totalWritten(), metrics.totalFailed(), metrics.totalDropped());
    }

    public PersistenceMetrics metrics() {
        return metrics;
    }

    /** false cuando el circuito esta abierto por fallos consecutivos de base de datos. */
    public boolean isHealthy() {
        return System.currentTimeMillis() >= circuitOpenUntilMs;
    }

    // --- Encolado ------------------------------------------------------------

    private void enqueue(PendingWrite write) {
        switch (config.backpressure()) {
            case BLOCK -> {
                try {
                    queue.put(write);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new PersistenceException("Encolado interrumpido", e);
                }
            }
            case DROP -> {
                if (!queue.offer(write)) {
                    metrics.recordDropped(write.topic());
                }
            }
            case FAIL -> {
                if (!queue.offer(write)) {
                    metrics.recordDropped(write.topic());
                    throw new PersistenceException(
                            "Cola de escritura llena (" + config.queueCapacity() + ")");
                }
            }
        }
    }

    private void ensureCircuitClosed() {
        if (!isHealthy()) {
            throw new PersistenceUnavailableException(
                    "Base de datos no disponible: circuito abierto tras "
                            + config.unhealthyThreshold() + " lotes fallidos consecutivos");
        }
    }

    // --- Escritura -----------------------------------------------------------

    private void writeLoop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                PendingWrite first = queue.poll(config.lingerMs() <= 0 ? 1 : config.lingerMs(),
                        TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                List<PendingWrite> batch = new ArrayList<>(config.batchSize());
                batch.add(first);
                queue.drainTo(batch, config.batchSize() - 1);
                inFlightBatches.incrementAndGet();
                try {
                    writeBatchWithRetries(batch);
                } finally {
                    inFlightBatches.decrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("Error inesperado en el escritor de outbox", e);
            }
        }
    }

    private void writeBatchWithRetries(List<PendingWrite> batch) {
        long backoff = config.retryBackoffMs();
        SQLException last = null;
        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            try {
                long start = System.nanoTime();
                writeBatch(batch);
                double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
                metrics.recordBatchCommitted(batch.size(), latencyMs);
                batch.forEach(write -> metrics.recordWritten(write.topic(), latencyMs));
                onBatchSuccess();
                return;
            } catch (SQLException e) {
                last = e;
                if (attempt < config.maxRetries()) {
                    metrics.recordRetry();
                    sleep(backoff);
                    backoff = Math.min(backoff * 2, config.maxRetryBackoffMs());
                }
            }
        }
        batch.forEach(write -> metrics.recordFailed(write.topic()));
        onBatchFailure(last, batch.size());
    }

    /**
     * Una transaccion por lote: filas de negocio y filas de outbox entran o no entran juntas.
     */
    private void writeBatch(List<PendingWrite> batch) throws SQLException {
        List<PreparedWrite> prepared = new ArrayList<>(batch.size());
        for (PendingWrite write : batch) {
            prepared.add(new PreparedWrite(write, recordMapper.toRecord(write.event(), write.topic())));
        }
        Map<String, List<PreparedWrite>> byDomain = groupByDomain(prepared);
        // preparar el DDL fuera de la transaccion del lote, para no retenerla mientras se crea
        Map<String, TableModel> tables = new LinkedHashMap<>();
        if (config.writeBusinessTable()) {
            for (String domain : byDomain.keySet()) {
                tables.put(domain, ddlExecutor.ensureTable(schemaCatalog.apply(domain)));
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (config.writeBusinessTable()) {
                    for (Map.Entry<String, List<PreparedWrite>> entry : byDomain.entrySet()) {
                        writeBusinessRows(connection, tables.get(entry.getKey()), entry.getValue());
                    }
                }
                writeOutboxRows(connection, prepared);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new PersistenceException("Fallo escribiendo el lote", e);
            }
        }
    }

    private Map<String, List<PreparedWrite>> groupByDomain(List<PreparedWrite> batch) {
        Map<String, List<PreparedWrite>> byDomain = new LinkedHashMap<>();
        for (PreparedWrite prepared : batch) {
            byDomain.computeIfAbsent(prepared.write().event().domain(), d -> new ArrayList<>())
                    .add(prepared);
        }
        return byDomain;
    }

    private void writeBusinessRows(Connection connection, TableModel table, List<PreparedWrite> writes)
            throws SQLException {
        String sql = insertSqlCache.computeIfAbsent(table.tableName(), name ->
                dialect.insertIgnoreConflict(name, table.columnNames(), DdlGenerator.COL_EVENT_ID));
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (PreparedWrite prepared : writes) {
                binder.bind(statement, table, prepared.write().event(), prepared.write().topic(),
                        prepared.record().aggregateId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void writeOutboxRows(Connection connection, List<PreparedWrite> batch) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(dialect.insertOutbox(config.outboxTable()))) {
            for (PreparedWrite prepared : batch) {
                OutboxRecord record = prepared.record();
                statement.setString(1, record.id());
                statement.setString(2, record.aggregateType());
                statement.setString(3, record.aggregateId());
                statement.setString(4, record.type());
                dialect.bindJson(statement, 5, record.payload());
                statement.setString(6, record.domain());
                statement.setString(7, record.traceId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void onBatchSuccess() {
        consecutiveBatchFailures.set(0);
        circuitOpenUntilMs = 0;
    }

    private void onBatchFailure(SQLException cause, int rows) {
        int failures = consecutiveBatchFailures.incrementAndGet();
        log.error("Lote de {} eventos descartado tras {} reintentos (fallo consecutivo {}): {}",
                rows, config.maxRetries(), failures, cause == null ? "?" : cause.getMessage());
        if (failures >= config.unhealthyThreshold()) {
            circuitOpenUntilMs = System.currentTimeMillis() + config.circuitResetMs();
            metrics.recordCircuitOpened();
            log.error("Circuito abierto durante {} ms: publish() empezara a lanzar excepciones",
                    config.circuitResetMs());
        }
    }

    private void sleep(long millis) {
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
