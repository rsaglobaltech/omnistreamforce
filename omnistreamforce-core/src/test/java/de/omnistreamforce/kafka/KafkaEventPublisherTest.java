package de.omnistreamforce.kafka;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.engine.TopicStats;
import de.omnistreamforce.serializer.JsonEventSerializer;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaEventPublisherTest {

    private static final String TOPIC = "fastfood-events";
    private static final String ERROR_TOPIC = "fastfood-errors";

    private Event event(String orderId, String eventType) {
        return new Event("id-" + orderId, eventType, "fastfood", "test", System.currentTimeMillis(),
                "1.0", Map.of("orderId", orderId), Map.of("eventName", "OrderPlaced"),
                "trace", "corr");
    }

    private MockProducer<String, byte[]> mockProducer() {
        return new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
    }

    @Test
    void metricsAreBrokenDownPerTopic() {
        try (MockProducer<String, byte[]> producer = mockProducer()) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(
                    producer, new JsonEventSerializer(), KeyStrategy.ENTITY_ID, "orderId");

            publisher.publish(event("ORD-1", "NORMAL"), TOPIC);
            publisher.publish(event("ORD-2", "NORMAL"), TOPIC);
            publisher.publish(event("ORD-3", "ERROR"), ERROR_TOPIC);

            Map<String, TopicStats> perTopic = publisher.metrics().perTopicStats();
            assertThat(perTopic).containsOnlyKeys(TOPIC, ERROR_TOPIC);
            assertThat(perTopic.get(TOPIC).totalSent()).isEqualTo(2);
            assertThat(perTopic.get(TOPIC).totalAcknowledged()).isEqualTo(2);
            assertThat(perTopic.get(ERROR_TOPIC).totalSent()).isEqualTo(1);
            assertThat(perTopic.get(TOPIC).totalFailed()).isZero();

            assertThat(publisher.metrics().totalSent()).isEqualTo(3);
            assertThat(publisher.metrics().totalAcknowledged()).isEqualTo(3);
        }
    }

    @Test
    void perTopicLatencyIsMeasuredNotLeftAtZero() {
        try (MockProducer<String, byte[]> producer = mockProducer()) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(
                    producer, new JsonEventSerializer(), KeyStrategy.RANDOM, null);

            for (int i = 0; i < 20; i++) {
                publisher.publish(event("ORD-" + i, "NORMAL"), TOPIC);
            }

            TopicStats stats = publisher.metrics().perTopicStats().get(TOPIC);
            assertThat(stats.avgLatencyMs()).isGreaterThan(0.0);
            assertThat(stats.avgLatencyMs()).isCloseTo(publisher.metrics().avgLatencyMs(),
                    org.assertj.core.data.Offset.offset(0.001));
        }
    }

    @Test
    void entityIdStrategyUsesConfiguredPayloadFieldAsRecordKey() {
        try (MockProducer<String, byte[]> producer = mockProducer()) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(
                    producer, new JsonEventSerializer(), KeyStrategy.ENTITY_ID, "orderId");

            publisher.publish(event("ORD-BK-123", "NORMAL"), TOPIC);

            List<ProducerRecord<String, byte[]>> history = producer.history();
            assertThat(history).hasSize(1);
            assertThat(history.get(0).topic()).isEqualTo(TOPIC);
            assertThat(history.get(0).key()).isEqualTo("ORD-BK-123");
            assertThat(history.get(0).value()).isNotEmpty();
        }
    }

    @Test
    void roundRobinStrategyProducesIncreasingKeys() {
        try (MockProducer<String, byte[]> producer = mockProducer()) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(
                    producer, new JsonEventSerializer(), KeyStrategy.ROUND_ROBIN, null);

            publisher.publish(event("ORD-1", "NORMAL"), TOPIC);
            publisher.publish(event("ORD-2", "NORMAL"), TOPIC);

            assertThat(producer.history().get(0).key()).isEqualTo("1");
            assertThat(producer.history().get(1).key()).isEqualTo("2");
        }
    }

    @Test
    void failuresAreCountedForTheAffectedTopic() {
        MockProducer<String, byte[]> producer = new MockProducer<>(
                false, new StringSerializer(), new ByteArraySerializer());
        try (producer) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(
                    producer, new JsonEventSerializer(), KeyStrategy.RANDOM, null);

            publisher.publish(event("ORD-1", "NORMAL"), ERROR_TOPIC);
            producer.errorNext(new RuntimeException("broker caido"));

            assertThat(publisher.metrics().totalFailed()).isEqualTo(1);
            assertThat(publisher.metrics().perTopicStats().get(ERROR_TOPIC).totalFailed()).isEqualTo(1);
            assertThat(publisher.metrics().perTopicStats().get(ERROR_TOPIC).totalAcknowledged()).isZero();
        }
    }
}
