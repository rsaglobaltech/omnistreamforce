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
}