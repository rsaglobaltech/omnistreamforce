package de.omnistreamforce.engine;

import de.omnistreamforce.core.Event;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompositePublisherTest {

    private static final String TOPIC = "fastfood-events";

    private Event event() {
        return new Event("id-1", "NORMAL", "fastfood", "test", System.currentTimeMillis(),
                "1.0", Map.of("orderId", "ORD-1"), Map.of("eventName", "OrderPlaced"), "t", "c");
    }

    /** Publisher que siempre falla, para comprobar que un destino roto no bloquea al otro. */
    private static final class BrokenPublisher implements EventPublisher {
        private final AtomicInteger closeCalls = new AtomicInteger();

        @Override
        public void publish(Event event, String topic) {
            throw new IllegalStateException("destino caido");
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
            throw new IllegalStateException("fallo al cerrar");
        }
    }

    @Test
    void everyDelegateReceivesTheEvent() {
        RecordingPublisher first = new RecordingPublisher();
        RecordingPublisher second = new RecordingPublisher();
        CompositePublisher composite = new CompositePublisher(List.of(first, second),
                CompositePublisher.FailurePolicy.CONTINUE);

        composite.publish(event(), TOPIC);

        assertThat(first.totalPublished()).isEqualTo(1);
        assertThat(second.totalPublished()).isEqualTo(1);
    }

    @Test
    void aBrokenDelegateDoesNotStarveTheOtherOne() {
        RecordingPublisher healthy = new RecordingPublisher();
        CompositePublisher composite = new CompositePublisher(
                List.of(new BrokenPublisher(), healthy), CompositePublisher.FailurePolicy.CONTINUE);

        composite.publish(event(), TOPIC);

        assertThat(healthy.totalPublished()).isEqualTo(1);
    }

    @Test
    void failFastRethrowsButStillReachesEveryDelegate() {
        RecordingPublisher healthy = new RecordingPublisher();
        CompositePublisher composite = new CompositePublisher(
                List.of(new BrokenPublisher(), healthy), CompositePublisher.FailurePolicy.FAIL_FAST);

        assertThatThrownBy(() -> composite.publish(event(), TOPIC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("destino caido");
        assertThat(healthy.totalPublished()).isEqualTo(1);
    }

    @Test
    void severalFailuresAreReportedTogether() {
        CompositePublisher composite = new CompositePublisher(
                List.of(new BrokenPublisher(), new BrokenPublisher()),
                CompositePublisher.FailurePolicy.FAIL_FAST);

        assertThatThrownBy(() -> composite.publish(event(), TOPIC))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
    }

    @Test
    void batchAndFlushReachEveryDelegate() {
        RecordingPublisher first = new RecordingPublisher();
        RecordingPublisher second = new RecordingPublisher();
        CompositePublisher composite = new CompositePublisher(List.of(first, second),
                CompositePublisher.FailurePolicy.CONTINUE);

        composite.publishBatch(List.of(event(), event()), TOPIC);
        composite.flush();

        assertThat(first.totalPublished()).isEqualTo(2);
        assertThat(second.totalPublished()).isEqualTo(2);
    }

    @Test
    void closeReachesEveryDelegateEvenIfOneThrows() {
        BrokenPublisher broken = new BrokenPublisher();
        RecordingPublisher healthy = new RecordingPublisher();
        CompositePublisher composite = new CompositePublisher(List.of(broken, healthy),
                CompositePublisher.FailurePolicy.CONTINUE);

        assertThatThrownBy(composite::close).isInstanceOf(IllegalStateException.class);

        assertThat(broken.closeCalls.get()).isEqualTo(1);
        assertThat(healthy.isClosed()).isTrue();
    }

    @Test
    void atLeastOneDelegateIsRequired() {
        assertThatThrownBy(() -> new CompositePublisher(List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompositePublisher(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiDomainEngineAcceptsItWithoutAnySignatureChange() {
        RecordingPublisher kafkaLike = new RecordingPublisher();
        RecordingPublisher dbLike = new RecordingPublisher();
        CompositePublisher composite = new CompositePublisher(List.of(kafkaLike, dbLike),
                CompositePublisher.FailurePolicy.CONTINUE);

        MultiDomainEngine engine = new MultiDomainEngine(null, composite);
        assertThat(engine.activeDomains()).isEmpty();
        engine.shutdown();

        assertThat(kafkaLike.isClosed()).isTrue();
        assertThat(dbLike.isClosed()).isTrue();
    }
}
