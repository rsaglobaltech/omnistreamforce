package de.omnistreamforce.engine;

import de.omnistreamforce.routing.TopicMapping;
import de.omnistreamforce.serializer.JsonEventSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultiDomainEngineTest {

    private MultiDomainEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    void publishesToMultipleTopicsInParallel() throws InterruptedException {
        RecordingPublisher publisher = new RecordingPublisher();
        engine = new MultiDomainEngine(null, publisher);

        engine.addDomain(new StubDomainGenerator("alpha"),
                new GenerationConfig("alpha", "alpha-events", "alpha-errors",
                        200, 20, PublishingMode.STEADY, 1, KeyStrategy.RANDOM, "entityId", 0, 0),
                new TopicMapping("alpha", "alpha-events", "alpha-errors", 3, (short) 1, false));
        engine.addDomain(new StubDomainGenerator("beta"),
                new GenerationConfig("beta", "beta-events", "beta-errors",
                        200, 10, PublishingMode.STEADY, 1, KeyStrategy.RANDOM, "entityId", 0, 0),
                new TopicMapping("beta", "beta-events", "beta-errors", 3, (short) 1, false));

        Thread.sleep(1200);
        engine.stopAll();

        assertThat(publisher.eventsByTopic()).containsKeys(
                "alpha-events", "alpha-errors", "beta-events", "beta-errors");
    }

    @Test
    void removeDomainInHotDoesNotStopOthers() throws InterruptedException {
        RecordingPublisher publisher = new RecordingPublisher();
        engine = new MultiDomainEngine(null, publisher);

        engine.addDomain(new StubDomainGenerator("alpha"),
                new GenerationConfig("alpha", "alpha-events", null,
                        150, 0, PublishingMode.STEADY, 0, KeyStrategy.RANDOM, "entityId", 0, 0),
                new TopicMapping("alpha", "alpha-events", null, 3, (short) 1, false));
        engine.addDomain(new StubDomainGenerator("beta"),
                new GenerationConfig("beta", "beta-events", null,
                        150, 0, PublishingMode.STEADY, 0, KeyStrategy.RANDOM, "entityId", 0, 0),
                new TopicMapping("beta", "beta-events", null, 3, (short) 1, false));

        Thread.sleep(500);
        boolean removed = engine.removeDomain("alpha");
        Thread.sleep(500);
        long betaAfterRemove = publisher.eventsForTopic("beta-events").size();
        Thread.sleep(400);
        long betaLater = publisher.eventsForTopic("beta-events").size();
        engine.stopAll();

        assertThat(removed).isTrue();
        assertThat(engine.hasDomain("alpha")).isFalse();
        assertThat(betaLater).isGreaterThan(betaAfterRemove);
    }

    @Test
    void aggregateStatsCombinesDomains() throws InterruptedException {
        RecordingPublisher publisher = new RecordingPublisher();
        engine = new MultiDomainEngine(new JsonEventSerializer(), publisher);

        engine.addDomain(new StubDomainGenerator("alpha"),
                new GenerationConfig("alpha", "alpha-events", "alpha-errors",
                        200, 30, PublishingMode.STEADY, 1, KeyStrategy.RANDOM, "entityId", 0, 0),
                new TopicMapping("alpha", "alpha-events", "alpha-errors", 3, (short) 1, false));

        Thread.sleep(1200);
        engine.stopAll();

        MultiDomainEngine.AggregateStats aggregate = engine.aggregateStats();
        assertThat(aggregate.totalEvents()).isGreaterThan(0L);
        assertThat(aggregate.totalErrors()).isGreaterThan(0L);
        assertThat(aggregate.perDomain()).containsKey("alpha");
        assertThat(aggregate.perTopic()).isNotEmpty();
    }
}