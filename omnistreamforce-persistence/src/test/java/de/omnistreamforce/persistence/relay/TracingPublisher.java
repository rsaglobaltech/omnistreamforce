package de.omnistreamforce.persistence.relay;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.engine.EventPublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publisher de prueba que anota sus llamadas en la misma traza que la base de datos simulada,
 * para poder comprobar el orden publish -> flush -> marcar.
 */
final class TracingPublisher implements EventPublisher {

    record Published(Event event, String topic) {
    }

    private final List<String> trace;
    final List<Published> published = Collections.synchronizedList(new ArrayList<>());
    final AtomicLong failures = new AtomicLong();
    private volatile boolean failOnPublish;

    TracingPublisher(List<String> trace) {
        this.trace = trace;
    }

    /** Simula un publisher que no consigue entregar: incrementa su contador de fallos. */
    void failEverything(boolean fail) {
        this.failOnPublish = fail;
    }

    long failureCount() {
        return failures.get();
    }

    @Override
    public void publish(Event event, String topic) {
        trace.add("publish");
        published.add(new Published(event, topic));
        if (failOnPublish) {
            failures.incrementAndGet();
        }
    }

    @Override
    public void flush() {
        trace.add("flush");
    }

    @Override
    public void close() {
        trace.add("close");
    }
}
