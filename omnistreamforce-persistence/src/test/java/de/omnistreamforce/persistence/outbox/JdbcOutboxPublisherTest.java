package de.omnistreamforce.persistence.outbox;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.fastfood.FastFoodGenerator;
import de.omnistreamforce.persistence.PersistenceConfig;
import de.omnistreamforce.persistence.PersistenceException;
import de.omnistreamforce.persistence.PersistenceUnavailableException;
import de.omnistreamforce.persistence.dialect.PostgresDialect;
import de.omnistreamforce.serializer.JsonEventSerializer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comportamiento del sink sin base de datos real: batching, transacciones, reintentos,
 * backpressure y circuito. La semantica SQL se prueba aparte contra Postgres real.
 */
class JdbcOutboxPublisherTest {

    private static final String TOPIC = "fastfood-events";

    private final FastFoodGenerator generator = new FastFoodGenerator();
    private final RecordingDatabase database = new RecordingDatabase();

    private JdbcOutboxPublisher publisher(PersistenceConfig config) {
        return new JdbcOutboxPublisher(database.dataSource(), new PostgresDialect(), config,
                new JsonEventSerializer(), SchemaCatalog.of(generator.getSchema()));
    }

    private PersistenceConfig.Builder config() {
        return PersistenceConfig.builder("jdbc:postgresql://localhost:5432/osf")
                .writerThreads(1)
                .batchSize(100)
                .lingerMs(10)
                .writeBusinessTable(true);
    }

    private Event event() {
        return generator.generateEvent(FastFoodGenerator.ORDER_PLACED);
    }

    @Test
    void eventsAreWrittenInBatchesWithOneTransactionEach() throws Exception {
        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(50).build())) {
            for (int i = 0; i < 200; i++) {
                publisher.publish(event(), TOPIC);
            }
            publisher.flush();

            assertThat(publisher.metrics().totalWritten()).isEqualTo(200);
            assertThat(publisher.metrics().batchesCommitted()).isBetween(4L, 20L);
            // una transaccion por lote, no una por evento
            assertThat(database.commits.get()).isEqualTo((int) publisher.metrics().batchesCommitted());
            assertThat(database.commits.get()).isLessThan(200);
            assertThat(database.rollbacks.get()).isZero();
            assertThat(database.batchSizes).allMatch(size -> size <= 50);
        }
    }

    @Test
    void businessRowAndOutboxRowAreWrittenInTheSameTransaction() throws Exception {
        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(10).build())) {
            for (int i = 0; i < 10; i++) {
                publisher.publish(event(), TOPIC);
            }
            publisher.flush();

            // 10 filas de negocio + 10 de outbox
            assertThat(database.rowsAdded.get()).isEqualTo(20);
            assertThat(database.preparedSql).anyMatch(sql -> sql.contains("osf_fastfood_events"));
            assertThat(database.preparedSql).anyMatch(sql -> sql.contains("aggregatetype"));
            // el lote puede cerrarse por tiempo antes de llenarse, pero cada commit cubre
            // las filas de negocio y las de outbox de ese lote
            assertThat(database.commits.get()).isEqualTo((int) publisher.metrics().batchesCommitted());
            assertThat(database.rollbacks.get()).isZero();
        }
    }

    @Test
    void aFailedOutboxInsertRollsBackTheWholeBatch() throws Exception {
        database.failOutboxInsertsOnly(true);
        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(10).maxRetries(0).build())) {
            for (int i = 0; i < 10; i++) {
                publisher.publish(event(), TOPIC);
            }
            publisher.flush();

            assertThat(database.commits.get()).isZero();
            assertThat(database.rollbacks.get()).isPositive();
            assertThat(publisher.metrics().totalWritten()).isZero();
            assertThat(publisher.metrics().totalFailed()).isEqualTo(10);
        }
    }

    @Test
    void aFailedBatchIsRetriedUpToTheConfiguredLimit() throws Exception {
        database.failFirstBatches(2);
        try (JdbcOutboxPublisher publisher = publisher(
                config().batchSize(5).maxRetries(3).retryBackoffMs(1).build())) {
            for (int i = 0; i < 5; i++) {
                publisher.publish(event(), TOPIC);
            }
            publisher.flush();

            assertThat(publisher.metrics().totalRetries()).isEqualTo(2);
            assertThat(publisher.metrics().totalWritten()).isEqualTo(5);
            assertThat(database.rollbacks.get()).isEqualTo(2);
            assertThat(database.commits.get()).isEqualTo(1);
        }
    }

    @Test
    void theCircuitOpensAfterConsecutiveFailuresAndPublishStartsFailing() throws Exception {
        database.failAllInserts(true);
        try (JdbcOutboxPublisher publisher = publisher(config()
                .batchSize(1)
                .maxRetries(0)
                .unhealthyThreshold(3)
                .circuitResetMs(60_000)
                .build())) {

            for (int i = 0; i < 3; i++) {
                publisher.publish(event(), TOPIC);
                publisher.flush();
            }

            assertThat(publisher.isHealthy()).isFalse();
            assertThat(publisher.metrics().circuitOpenCount()).isPositive();
            assertThatThrownBy(() -> publisher.publish(event(), TOPIC))
                    .isInstanceOf(PersistenceUnavailableException.class);
        }
    }

    @Test
    void theCircuitClosesAgainAfterASuccessfulBatch() throws Exception {
        database.failFirstBatches(3);
        try (JdbcOutboxPublisher publisher = publisher(config()
                .batchSize(1)
                .maxRetries(0)
                .unhealthyThreshold(3)
                .circuitResetMs(1)
                .build())) {

            for (int i = 0; i < 3; i++) {
                publisher.publish(event(), TOPIC);
                publisher.flush();
            }
            assertThat(publisher.metrics().circuitOpenCount()).isPositive();

            Thread.sleep(20);
            publisher.publish(event(), TOPIC);
            publisher.flush();

            assertThat(publisher.isHealthy()).isTrue();
            assertThat(publisher.metrics().totalWritten()).isEqualTo(1);
        }
    }

    @Test
    void dropPolicyDiscardsInsteadOfBlockingWhenTheQueueIsFull() throws Exception {
        // cola diminuta y escritor con linger largo: se llena seguro
        try (JdbcOutboxPublisher publisher = publisher(config()
                .batchSize(1)
                .queueCapacity(1)
                .lingerMs(500)
                .backpressure(BackpressurePolicy.DROP)
                .build())) {

            for (int i = 0; i < 200; i++) {
                publisher.publish(event(), TOPIC);
            }

            assertThat(publisher.metrics().totalDropped()).isPositive();
        }
    }

    @Test
    void failPolicyThrowsWhenTheQueueIsFull() throws Exception {
        try (JdbcOutboxPublisher publisher = publisher(config()
                .batchSize(1)
                .queueCapacity(1)
                .lingerMs(500)
                .backpressure(BackpressurePolicy.FAIL)
                .build())) {

            assertThatThrownBy(() -> {
                for (int i = 0; i < 500; i++) {
                    publisher.publish(event(), TOPIC);
                }
            }).isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void publishIsSafeFromManyDomainThreadsAtOnce() throws Exception {
        int threads = 8;
        int perThread = 1000;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(threads);

        try (JdbcOutboxPublisher publisher = publisher(config().writerThreads(4).batchSize(100).build())) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int t = 0; t < threads; t++) {
                    executor.submit(() -> {
                        try {
                            for (int i = 0; i < perThread; i++) {
                                publisher.publish(event(), TOPIC);
                            }
                        } catch (Throwable e) {
                            failure.compareAndSet(null, e);
                        } finally {
                            done.countDown();
                        }
                    });
                }
                assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
            }
            publisher.flush();

            assertThat(failure.get()).isNull();
            assertThat(publisher.metrics().totalEnqueued()).isEqualTo((long) threads * perThread);
            assertThat(publisher.metrics().totalWritten()).isEqualTo((long) threads * perThread);
        }
    }

    @Test
    void closeDrainsWhatIsStillQueued() throws Exception {
        JdbcOutboxPublisher publisher = publisher(config().batchSize(100).lingerMs(50).build());
        for (int i = 0; i < 500; i++) {
            publisher.publish(event(), TOPIC);
        }
        publisher.close();

        assertThat(publisher.metrics().totalWritten()).isEqualTo(500);
        assertThat(publisher.metrics().queueDepth()).isZero();
    }

    @Test
    void publishingAfterCloseIsRejected() {
        JdbcOutboxPublisher publisher = publisher(config().build());
        publisher.close();

        assertThatThrownBy(() -> publisher.publish(event(), TOPIC))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void closeIsIdempotent() {
        JdbcOutboxPublisher publisher = publisher(config().build());
        publisher.close();
        publisher.close();
    }

    @Test
    void perTopicMetricsSeparateNormalAndErrorTopics() throws Exception {
        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(10).build())) {
            for (int i = 0; i < 6; i++) {
                publisher.publish(event(), TOPIC);
            }
            for (int i = 0; i < 4; i++) {
                publisher.publish(generator.generateErrorEvent(), "fastfood-errors");
            }
            publisher.flush();

            assertThat(publisher.metrics().perTopicStats()).containsOnlyKeys(TOPIC, "fastfood-errors");
            assertThat(publisher.metrics().perTopicStats().get(TOPIC).totalAcknowledged()).isEqualTo(6);
            assertThat(publisher.metrics().perTopicStats().get("fastfood-errors").totalAcknowledged()).isEqualTo(4);
        }
    }
}
