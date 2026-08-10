package de.omnistreamforce.persistence.relay;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.fastfood.FastFoodGenerator;
import de.omnistreamforce.persistence.dialect.PostgresDialect;
import de.omnistreamforce.serializer.JsonEventSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Maquina de estados del relay sin base de datos ni broker.
 */
class OutboxRelayTest {

    private static final String TOPIC = "fastfood-events";

    private final FastFoodGenerator generator = new FastFoodGenerator();
    private final JsonEventSerializer serializer = new JsonEventSerializer();
    private final FakeOutboxDatabase database = new FakeOutboxDatabase();
    private final TracingPublisher publisher = new TracingPublisher(database.actions);

    private OutboxRelay relay(RelayConfig config) {
        return new OutboxRelay(database.dataSource(), new PostgresDialect(), publisher,
                serializer, config, publisher::failureCount);
    }

    private RelayConfig.Builder config() {
        return RelayConfig.builder().batchSize(10).maxAttempts(3).retryBackoffMs(0);
    }

    private Map<String, Object> rowFor(String id, String key, int attempts) {
        Event event = generator.generateEvent(FastFoodGenerator.ORDER_PLACED);
        String payload = new String(serializer.serialize(event), StandardCharsets.UTF_8);
        return FakeOutboxDatabase.row(id, TOPIC, key, payload, attempts);
    }

    @Test
    void rowsArePublishedFlushedAndOnlyThenMarked() {
        database.enqueueBatch(List.of(rowFor("e1", "ORD-1", 0), rowFor("e2", "ORD-2", 0)));

        int processed = relay(config().build()).drainOnce();

        assertThat(processed).isEqualTo(2);
        assertThat(publisher.published).hasSize(2);
        // el orden es lo que da la garantia at-least-once
        assertThat(database.actions).containsExactly(
                "select", "publish", "publish", "flush", "update:PUBLISHED", "commit");
    }

    @Test
    void theMessageKeyIsRebuiltFromTheAggregateId() {
        database.enqueueBatch(List.of(rowFor("e1", "ORD-BK-99", 0)));

        relay(config().build()).drainOnce();

        Event republished = publisher.published.get(0).event();
        assertThat(republished.metadata().get("kafka.key")).isEqualTo("ORD-BK-99");
        assertThat(publisher.published.get(0).topic()).isEqualTo(TOPIC);
    }

    @Test
    void nothingIsMarkedAsPublishedWhenThePublisherFails() {
        publisher.failEverything(true);
        database.enqueueBatch(List.of(rowFor("e1", "ORD-1", 0)));

        relay(config().build()).drainOnce();

        assertThat(database.actions).contains("update:FAILED");
        assertThat(database.actions).doesNotContain("update:PUBLISHED");
        assertThat(database.commits.get()).isEqualTo(1);
    }

    @Test
    void rowsThatExhaustTheirAttemptsAreCountedAsDeadLettered() {
        publisher.failEverything(true);
        // attempts=2 y maxAttempts=3: este intento agota el ultimo
        database.enqueueBatch(List.of(rowFor("e1", "ORD-1", 2), rowFor("e2", "ORD-2", 0)));

        OutboxRelay relay = relay(config().maxAttempts(3).build());
        relay.drainOnce();

        assertThat(relay.stats().deadLettered()).isEqualTo(1);
        assertThat(relay.stats().failed()).isEqualTo(2);
        assertThat(relay.stats().published()).isZero();
    }

    @Test
    void deleteAfterPublishRemovesTheRowInsteadOfMarkingIt() {
        database.enqueueBatch(List.of(rowFor("e1", "ORD-1", 0)));

        relay(config().deleteAfterPublish(true).build()).drainOnce();

        assertThat(database.actions).contains("delete");
        assertThat(database.actions).doesNotContain("update:PUBLISHED");
    }

    @Test
    void anEmptyOutboxCommitsAndPublishesNothing() {
        OutboxRelay relay = relay(config().build());

        assertThat(relay.drainOnce()).isZero();
        assertThat(publisher.published).isEmpty();
        assertThat(database.actions).containsExactly("select", "commit");
        assertThat(relay.stats().polls()).isEqualTo(1);
    }

    @Test
    void statsAccumulateAcrossBatches() {
        database.enqueueBatch(List.of(rowFor("e1", "ORD-1", 0), rowFor("e2", "ORD-2", 0)));
        database.enqueueBatch(List.of(rowFor("e3", "ORD-3", 0)));

        OutboxRelay relay = relay(config().build());
        relay.drainOnce();
        relay.drainOnce();

        assertThat(relay.stats().published()).isEqualTo(3);
        assertThat(relay.stats().claimed()).isEqualTo(3);
        assertThat(relay.stats().batches()).isEqualTo(2);
        assertThat(relay.stats().avgBatchSize()).isEqualTo(1.5);
    }

    @Test
    void shardingPredicateIsOnlyAddedWithSeveralWorkers() {
        database.enqueueBatch(List.of(rowFor("e1", "ORD-1", 0)));
        relay(config().workerThreads(1).build()).drainOnce();
        assertThat(database.executedSql).noneMatch(sql -> sql.contains("hashtext"));

        FakeOutboxDatabase sharded = new FakeOutboxDatabase();
        sharded.enqueueBatch(List.of(rowFor("e2", "ORD-2", 0)));
        new OutboxRelay(sharded.dataSource(), new PostgresDialect(),
                new TracingPublisher(sharded.actions), serializer,
                config().workerThreads(4).build(), () -> 0L).drainOnce();

        assertThat(sharded.executedSql).anyMatch(sql ->
                sql.contains("hashtext(aggregateid)) % 4) = 0") && sql.contains("ORDER BY seq"));
    }

    @Test
    void startAndStopAreIdempotent() {
        OutboxRelay relay = relay(config().pollIntervalMs(50).build());
        relay.start();
        relay.start();
        assertThat(relay.isRunning()).isTrue();
        relay.stop();
        relay.stop();
        assertThat(relay.isRunning()).isFalse();
    }

    @Test
    void aSharedPublisherIsNotClosedByTheRelay() {
        OutboxRelay relay = relay(config().ownsPublisher(false).build());
        relay.start();
        relay.stop();
        assertThat(database.actions).doesNotContain("close");
    }
}
