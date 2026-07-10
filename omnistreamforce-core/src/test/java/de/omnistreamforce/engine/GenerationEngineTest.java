package de.omnistreamforce.engine;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.routing.DefaultTopicRouter;
import de.omnistreamforce.routing.DomainTopicConfig;
import de.omnistreamforce.routing.TopicMapping;
import de.omnistreamforce.serializer.JsonEventSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationEngineTest {

    private GenerationEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.stop();
        }
    }

    private DefaultTopicRouter stubRouter() {
        TopicMapping mapping = new TopicMapping(StubDomainGenerator.DOMAIN, "stub-events",
                "stub-errors", 3, (short) 1, false);
        return new DefaultTopicRouter(new DomainTopicConfig(List.of(mapping), true, "-errors"));
    }

    private GenerationConfig steadyConfig(String domain, String topic, String errorTopic,
                                          int eps, double errorRate, long duration, String keyField) {
        return new GenerationConfig(domain, topic, errorTopic, eps, errorRate,
                PublishingMode.STEADY, duration, KeyStrategy.ENTITY_ID, keyField, 0, 0);
    }

    @Test
    void steadyRateApproachesConfiguredEps() throws InterruptedException {
        RecordingPublisher publisher = new RecordingPublisher();
        engine = new GenerationEngine(new StubDomainGenerator(),
                steadyConfig(StubDomainGenerator.DOMAIN, "stub-events", "stub-errors", 300, 0, 1, "entityId"),
                null, stubRouter(), publisher, new ErrorInjector());
        engine.start();

        Thread.sleep(1200);
        engine.stop();
        publisher.close();

        GenerationStats.Snapshot snap = engine.snapshot();
        assertThat(snap.totalEvents()).isBetween(150L, 600L);
        assertThat(snap.perTopicStats()).containsKey("stub-events");
    }

    @Test
    void errorRateApproachesConfigured() throws InterruptedException {
        RecordingPublisher publisher = new RecordingPublisher();
        engine = new GenerationEngine(new StubDomainGenerator(),
                steadyConfig(StubDomainGenerator.DOMAIN, "stub-events", "stub-errors", 400, 30, 1, "entityId"),
                new JsonEventSerializer(), stubRouter(), publisher, new ErrorInjector());
        engine.start();

        Thread.sleep(1200);
        engine.stop();

        GenerationStats.Snapshot snap = engine.snapshot();
        double observedRate = snap.totalEvents() == 0 ? 0
                : (snap.totalErrors() * 100.0) / snap.totalEvents();
        assertThat(snap.totalEvents()).isGreaterThan(100L);
        assertThat(observedRate).isBetween(18.0, 45.0);
        assertThat(publisher.eventsByTopic()).containsKeys("stub-events", "stub-errors");
    }

    @Test
    void pauseAndResumeControlGeneration() throws InterruptedException {
        RecordingPublisher publisher = new RecordingPublisher();
        engine = new GenerationEngine(new StubDomainGenerator(),
                steadyConfig(StubDomainGenerator.DOMAIN, "stub-events", null, 200, 0, 0, "entityId"),
                null, stubRouter(), publisher, new ErrorInjector());
        engine.start();

        Thread.sleep(400);
        long countAfterStart = publisher.totalPublished();
        engine.pause();
        Thread.sleep(500);
        long countAfterPause = publisher.totalPublished();
        engine.resume();
        Thread.sleep(500);
        long countAfterResume = publisher.totalPublished();
        engine.stop();

        assertThat(countAfterStart).isGreaterThan(0L);
        assertThat(countAfterResume).isGreaterThan(countAfterPause);
        assertThat(engine.isPaused()).isFalse();
    }

    @Test
    void stopStopsCleanly() throws InterruptedException {
        RecordingPublisher publisher = new RecordingPublisher();
        engine = new GenerationEngine(new StubDomainGenerator(),
                steadyConfig(StubDomainGenerator.DOMAIN, "stub-events", null, 200, 0, 0, "entityId"),
                null, stubRouter(), publisher, new ErrorInjector());
        engine.start();
        Thread.sleep(300);
        engine.stop();
        long countAtStop = publisher.totalPublished();
        Thread.sleep(500);
        assertThat(publisher.totalPublished()).isEqualTo(countAtStop);
        assertThat(engine.isRunning()).isFalse();
    }

    @Test
    void keyStrategyEntityIdUsesPayloadField() throws InterruptedException {
        RecordingPublisher publisher = new RecordingPublisher();
        TopicMapping mapping = new TopicMapping(StubDomainGenerator.DOMAIN, "stub-events",
                null, 3, (short) 1, false);
        DefaultTopicRouter router = new DefaultTopicRouter(new DomainTopicConfig(List.of(mapping), false, "-errors"));
        engine = new GenerationEngine(new StubDomainGenerator(),
                steadyConfig(StubDomainGenerator.DOMAIN, "stub-events", null, 100, 0, 0, "entityId"),
                null, router, publisher, new ErrorInjector());
        engine.start();
        Thread.sleep(400);
        engine.stop();

        List<Event> events = publisher.eventsForTopic("stub-events");
        assertThat(events).isNotEmpty();
        Event first = events.get(0);
        assertThat(first.metadata()).containsKey("kafka.key");
        assertThat(first.metadata().get("kafka.key")).isEqualTo(first.payload().get("entityId").toString());
    }

    @Test
    void allErrorTypesAreEventuallyGenerated() throws InterruptedException {
        RecordingPublisher publisher = new RecordingPublisher();
        ErrorInjector injector = new ErrorInjector();
        engine = new GenerationEngine(new StubDomainGenerator(),
                steadyConfig(StubDomainGenerator.DOMAIN, "stub-events", "stub-errors", 500, 100, 1, "entityId"),
                null, stubRouter(), publisher, injector);
        engine.start();
        Thread.sleep(1500);
        engine.stop();

        assertThat(injector.allErrorTypesGenerated(new StubDomainGenerator())).isTrue();
    }
}