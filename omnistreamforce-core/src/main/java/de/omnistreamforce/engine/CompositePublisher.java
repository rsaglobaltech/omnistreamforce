package de.omnistreamforce.engine;

import de.omnistreamforce.core.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Publica el mismo evento en varios destinos a la vez (por ejemplo Kafka y base de datos).
 * <p>
 * Recorre <b>todos</b> los delegados aunque alguno falle: que un sink este roto no debe dejar
 * sin datos al otro. Las excepciones se acumulan y, segun la politica, se relanzan o solo se
 * registran.
 */
public final class CompositePublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CompositePublisher.class);

    public enum FailurePolicy {
        /** Relanza el primer fallo (con los demas como suppressed) para que el motor lo cuente. */
        FAIL_FAST,
        /** Registra el fallo y sigue: prima mantener el flujo aunque un destino falle. */
        CONTINUE
    }

    private final List<EventPublisher> delegates;
    private final FailurePolicy policy;

    public CompositePublisher(List<EventPublisher> delegates, FailurePolicy policy) {
        if (delegates == null || delegates.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos un publisher");
        }
        this.delegates = List.copyOf(delegates);
        this.policy = policy == null ? FailurePolicy.CONTINUE : policy;
    }

    public List<EventPublisher> delegates() {
        return delegates;
    }

    @Override
    public void publish(Event event, String topic) {
        forEachDelegate(delegate -> delegate.publish(event, topic), "publicando el evento");
    }

    @Override
    public void publishBatch(List<Event> events, String topic) {
        forEachDelegate(delegate -> delegate.publishBatch(events, topic), "publicando el lote");
    }

    @Override
    public void flush() {
        forEachDelegate(EventPublisher::flush, "vaciando el publisher");
    }

    @Override
    public void close() {
        // cerrar siempre todos, pase lo que pase con los anteriores
        RuntimeException failure = null;
        for (EventPublisher delegate : delegates) {
            try {
                delegate.close();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void forEachDelegate(java.util.function.Consumer<EventPublisher> action, String what) {
        List<RuntimeException> failures = null;
        for (EventPublisher delegate : delegates) {
            try {
                action.accept(delegate);
            } catch (RuntimeException e) {
                if (failures == null) {
                    failures = new ArrayList<>(2);
                }
                failures.add(e);
            }
        }
        if (failures == null) {
            return;
        }
        if (policy == FailurePolicy.FAIL_FAST) {
            RuntimeException first = failures.get(0);
            failures.stream().skip(1).forEach(first::addSuppressed);
            throw first;
        }
        for (RuntimeException failure : failures) {
            log.warn("Un destino fallo {}: {}", what, failure.toString());
        }
    }
}
