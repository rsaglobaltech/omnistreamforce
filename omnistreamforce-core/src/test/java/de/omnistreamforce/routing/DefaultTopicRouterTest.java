package de.omnistreamforce.routing;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTopicRouterTest {

    @Test
    void routesNormalEventToDomainTopic() {
        TopicMapping mapping = new TopicMapping(
                "ecommerce", "ecommerce-orders", "ecommerce-errors", 3, (short) 1, false);
        DomainTopicConfig config = new DomainTopicConfig(List.of(mapping), true, "-errors");
        DefaultTopicRouter router = new DefaultTopicRouter(config);

        Event event = new Event("id", EventType.NORMAL.name(), "ecommerce", "src",
                1L, "1.0", java.util.Map.of(), java.util.Map.of(), "t", "c");

        assertThat(router.route(event)).isEqualTo("ecommerce-orders");
    }

    @Test
    void routesErrorEventToSeparateTopicWhenConfigured() {
        TopicMapping mapping = new TopicMapping(
                "ecommerce", "ecommerce-orders", "ecommerce-errors", 3, (short) 1, false);
        DomainTopicConfig config = new DomainTopicConfig(List.of(mapping), true, "-errors");
        DefaultTopicRouter router = new DefaultTopicRouter(config);

        Event event = new Event("id", EventType.ERROR.name(), "ecommerce", "src",
                1L, "1.0", java.util.Map.of(), java.util.Map.of(), "t", "c");

        assertThat(router.route(event)).isEqualTo("ecommerce-errors");
    }

    @Test
    void routesErrorEventToSameTopicWhenNotSeparate() {
        TopicMapping mapping = new TopicMapping(
                "ecommerce", "ecommerce-orders", null, 3, (short) 1, false);
        DomainTopicConfig config = new DomainTopicConfig(List.of(mapping), false, "-errors");
        DefaultTopicRouter router = new DefaultTopicRouter(config);

        Event event = new Event("id", EventType.ERROR.name(), "ecommerce", "src",
                1L, "1.0", java.util.Map.of(), java.util.Map.of(), "t", "c");

        assertThat(router.route(event)).isEqualTo("ecommerce-orders");
    }

    @Test
    void routingIsSafeWhileDomainsAreAddedAndRemovedOnTheFly() throws Exception {
        TopicMapping stable = new TopicMapping(
                "ecommerce", "ecommerce-orders", "ecommerce-errors", 3, (short) 1, false);
        DefaultTopicRouter router = new DefaultTopicRouter(
                new DomainTopicConfig(List.of(stable), true, "-errors"));

        Event event = new Event("id", EventType.NORMAL.name(), "ecommerce", "src",
                1L, "1.0", java.util.Map.of(), java.util.Map.of(), "t", "c");

        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.CountDownLatch readersDone = new java.util.concurrent.CountDownLatch(4);

        try (java.util.concurrent.ExecutorService executor =
                     java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < 4; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < 5000; i++) {
                            assertThat(router.route(event)).isEqualTo("ecommerce-orders");
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        readersDone.countDown();
                    }
                });
            }
            for (int i = 0; i < 5000; i++) {
                router.addMapping(new TopicMapping(
                        "hot-" + i, "hot-events", null, 1, (short) 1, false));
                router.removeMapping("hot-" + i);
            }
            assertThat(readersDone.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failure.get()).isNull();
    }
}