package de.omnistreamforce.engine;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.DomainGenerator;
import de.omnistreamforce.routing.TopicRouter;
import de.omnistreamforce.serializer.EventSerializer;
import de.omnistreamforce.util.RandomUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orquestador del ciclo de generacion para un unico dominio/topic.
 * <p>
 * En cada tick del {@link EventScheduler} genera {@code N} eventos, decide aleatoriamente
 * si son normales o de error (segun {@link ErrorInjector}), los serializa y los publica
 * en el topic resuelto por el {@link TopicRouter}.
 */
public class GenerationEngine {

    private final DomainGenerator generator;
    private final GenerationConfig config;
    private final EventSerializer serializer;
    private final TopicRouter router;
    private final EventPublisher publisher;
    private final ErrorInjector errorInjector;
    private final EventScheduler scheduler;
    private final GenerationStats stats = new GenerationStats();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicLong roundRobin = new AtomicLong();
    private volatile double currentErrorRate;

    public GenerationEngine(DomainGenerator generator, GenerationConfig config,
                            EventSerializer serializer, TopicRouter router,
                            EventPublisher publisher, ErrorInjector errorInjector) {
        this.generator = generator;
        this.config = config;
        this.serializer = serializer;
        this.router = router;
        this.publisher = publisher;
        this.errorInjector = errorInjector;
        this.scheduler = new EventScheduler(
                config.publishingMode(),
                config.eventsPerSecond(),
                100,
                config.burstSize(),
                config.rampTargetEPS()
        );
        this.currentErrorRate = config.errorRate();
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        // No se toca aqui el estado de pausa: MultiDomainEngine arranca cada dominio en otro
        // hilo, asi que una pausa pedida entre el alta y el arranque se perderia. El flag lo
        // limpia stop().
        long deadlineMs = config.isUnlimited() ? Long.MAX_VALUE : System.currentTimeMillis() + config.durationSeconds() * 1000;
        scheduler.start(() -> tick(deadlineMs));
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.stop();
        }
        // se limpia siempre, incluso si el arranque asincrono aun no habia corrido,
        // para que un start() posterior publique en vez de quedarse pausado
        paused.set(false);
    }

    private void tick(long deadlineMs) {
        if (paused.get() || !running.get()) {
            return;
        }
        if (System.currentTimeMillis() >= deadlineMs) {
            stop();
            return;
        }
        int tokens = scheduler.tokensForTick();
        for (int i = 0; i < tokens; i++) {
            produceEvent();
        }
        stats.tickWindow();
    }

    private void produceEvent() {
        boolean isError = errorInjector.shouldGenerateError(currentErrorRate);
        Event event;
        if (isError) {
            String errorType = errorInjector.nextErrorType(generator);
            event = generator.generateEvent(errorType);
            event = ErrorInjector.withSeverity(event, errorInjector.nextSeverity());
        } else {
            String normalType = RandomUtils.randomFrom(generator.getSupportedEventTypes());
            event = generator.generateEvent(normalType);
        }
        event = applyKeyStrategy(event);

        long start = System.nanoTime();
        String topic;
        try {
            topic = router.route(event);
        } catch (RuntimeException e) {
            stats.recordFailure(config.topicName());
            return;
        }
        long bytes = serializeSize(event);
        try {
            publisher.publish(event, topic);
            double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
            stats.recordEvent(topic, isError, latencyMs, bytes);
        } catch (RuntimeException e) {
            stats.recordFailure(topic);
        }
    }

    private Event applyKeyStrategy(Event event) {
        String key = switch (config.keyStrategy()) {
            case RANDOM -> UUID.randomUUID().toString();
            case ENTITY_ID -> {
                Object field = event.payload().get(config.keyField());
                yield field == null ? UUID.randomUUID().toString() : field.toString();
            }
            case ROUND_ROBIN -> String.valueOf(roundRobin.incrementAndGet());
        };
        java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>(event.metadata());
        metadata.put("kafka.key", key);
        return new Event(
                event.eventId(), event.eventType(), event.domain(), event.source(), event.timestamp(),
                event.schemaVersion(), event.payload(), metadata, event.traceId(), event.correlationId()
        );
    }

    private long serializeSize(Event event) {
        if (serializer == null) {
            return 0;
        }
        try {
            byte[] data = serializer.serialize(event);
            return data == null ? 0 : data.length;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public GenerationConfig config() {
        return config;
    }

    public DomainGenerator generator() {
        return generator;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    public GenerationStats.Snapshot snapshot() {
        return stats.snapshot();
    }

    public GenerationStats stats() {
        return stats;
    }

    public void setEventsPerSecond(int eps) {
        scheduler.setEventsPerSecond(eps);
    }

    /** Ritmo objetivo actual, que puede diferir del configurado si se ajusto en caliente. */
    public int currentEventsPerSecond() {
        return scheduler.getTargetEPS();
    }

    public double currentErrorRate() {
        return currentErrorRate;
    }

    public void setErrorRate(double errorRate) {
        if (errorRate < 0.0) errorRate = 0.0;
        if (errorRate > 100.0) errorRate = 100.0;
        this.currentErrorRate = errorRate;
    }
}