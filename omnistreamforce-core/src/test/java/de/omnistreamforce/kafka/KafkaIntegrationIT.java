package de.omnistreamforce.kafka;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.serializer.JsonEventSerializer;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integracion (sufijo IT): lo ejecuta failsafe con {@code mvn verify -Pit}, no surefire.
 */
class KafkaIntegrationIT {

    private static KafkaContainer kafka;
    private static String bootstrap;

    @BeforeAll
    static void startKafka() {
        try {
            kafka = new KafkaContainer("apache/kafka:3.7.0");
            kafka.start();
            bootstrap = kafka.getBootstrapServers();
        } catch (RuntimeException e) {
            // Docker/Testcontainers no disponible o no detectable en este entorno: se omiten los tests.
            // No basta con IllegalStateException: en Windows la deteccion de estrategia puede fallar
            // con InvalidPathException si el PATH contiene comillas.
            kafka = null;
            Assumptions.assumeTrue(false, "Docker no disponible para Testcontainers: " + e);
        }
    }

    @AfterAll
    static void stopKafka() {
        if (kafka != null) {
            kafka.stop();
        }
    }

    private KafkaConnectionConfig config() {
        return KafkaConnectionConfig.builder()
                .clusterType(ClusterType.LOCAL)
                .bootstrapServers(bootstrap)
                .securityProtocol("PLAINTEXT")
                .acks("1")
                .retries(2)
                .build();
    }

    private Consumer<String, byte[]> consumer(String group) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrap);
        props.put("group.id", group);
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        return new KafkaConsumer<>(props);
    }

    private List<ConsumerRecord<String, byte[]>> drain(Consumer<String, byte[]> c, Duration total) {
        List<ConsumerRecord<String, byte[]>> all = new ArrayList<>();
        long deadline = System.currentTimeMillis() + total.toMillis();
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, byte[]> r : c.poll(Duration.ofMillis(300))) {
                all.add(r);
            }
        }
        return all;
    }

    @Test
    void connectionIsValidatedByListingTopics() {
        try (KafkaConnectionManager manager = new KafkaConnectionManager(config())) {
            Set<String> topics = manager.connect();
            assertThat(topics).isNotNull();
            assertThat(manager.clusterInfo().clusterType()).isEqualTo(ClusterType.LOCAL);
        }
    }

    @Test
    void publishesAndConsumesEvents() throws Exception {
        String topic = "it-events-" + UUID.randomUUID();
        try (KafkaConnectionManager manager = new KafkaConnectionManager(config())) {
            manager.admin().createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        }
        try (KafkaConnectionManager manager = new KafkaConnectionManager(config())) {
            Producer<String, byte[]> producer = manager.producer();
            JsonEventSerializer serializer = new JsonEventSerializer();
            KafkaEventPublisher publisher = new KafkaEventPublisher(producer, serializer,
                    KeyStrategy.ENTITY_ID, "patientId");

            for (int i = 0; i < 10; i++) {
                publisher.publish(buildEvent("PatientAdmission"), topic);
            }
            producer.flush();

            try (Consumer<String, byte[]> c = consumer("it-consume-" + UUID.randomUUID())) {
                c.subscribe(List.of(topic));
                List<ConsumerRecord<String, byte[]>> records = drain(c, Duration.ofSeconds(10));
                assertThat(records).hasSize(10);
                assertThat(records.get(0).key()).isEqualTo("PAT-123456");
                assertThat(records).allMatch(r -> r.value() != null && r.value().length > 0);
            }
            assertThat(publisher.metrics().totalSent()).isEqualTo(10);
            assertThat(publisher.metrics().totalAcknowledged()).isGreaterThan(0L);
        }
    }

    @Test
    void publishesToMultipleTopicsSimultaneously() throws Exception {
        String topicA = "it-multi-a-" + UUID.randomUUID();
        String topicB = "it-multi-b-" + UUID.randomUUID();
        try (KafkaConnectionManager manager = new KafkaConnectionManager(config())) {
            manager.admin().createTopics(List.of(
                    new NewTopic(topicA, 1, (short) 1),
                    new NewTopic(topicB, 1, (short) 1)
            )).all().get();
        }
        try (KafkaConnectionManager manager = new KafkaConnectionManager(config())) {
            Producer<String, byte[]> producer = manager.producer();
            KafkaEventPublisher publisher = new KafkaEventPublisher(producer, new JsonEventSerializer(),
                    KeyStrategy.RANDOM, null);

            for (int i = 0; i < 5; i++) {
                publisher.publish(buildEvent("OrderCreated"), topicA);
                publisher.publish(buildEvent("PatientDischarged"), topicB);
            }
            producer.flush();

            try (Consumer<String, byte[]> c = consumer("it-multi-" + UUID.randomUUID())) {
                c.subscribe(List.of(topicA, topicB));
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (ConsumerRecord<String, byte[]> r : drain(c, Duration.ofSeconds(10))) {
                    counts.merge(r.topic(), 1, Integer::sum);
                }
                assertThat(counts).containsEntry(topicA, 5).containsEntry(topicB, 5);
            }
        }
    }

    @Test
    void topicManagerCreatesAndListsTopics() throws Exception {
        String topic = "it-managed-" + UUID.randomUUID();
        try (KafkaConnectionManager manager = new KafkaConnectionManager(config())) {
            KafkaTopicManager topicManager = new KafkaTopicManager(manager.admin(), config().toAdminProps());
            assertThat(topicManager.createTopic(topic, 3, (short) 1)).isTrue();
            assertThat(topicManager.topicExists(topic)).isTrue();

            Map<String, Integer> withPartitions = topicManager.listTopicsWithPartitions();
            assertThat(withPartitions).containsEntry(topic, 3);

            TopicInfo info = topicManager.getTopicInfo(topic);
            assertThat(info.partitions()).isEqualTo(3);
        }
    }

    @Test
    void verifyOrCreateTopicCreatesIfMissing() {
        String topic = "it-auto-" + UUID.randomUUID();
        try (KafkaConnectionManager manager = new KafkaConnectionManager(config())) {
            KafkaTopicManager topicManager = new KafkaTopicManager(manager.admin(), config().toAdminProps());
            boolean created = topicManager.verifyOrCreateTopic(topic, 1, (short) 1, true);
            assertThat(created).isTrue();
            boolean secondCall = topicManager.verifyOrCreateTopic(topic, 1, (short) 1, true);
            assertThat(secondCall).isTrue();
        }
    }

    @Test
    void clusterInfoContainsBrokers() {
        try (KafkaConnectionManager manager = new KafkaConnectionManager(config())) {
            ClusterInfo info = manager.clusterInfo();
            assertThat(info.brokers()).isNotEmpty();
            assertThat(info.clusterId()).isNotBlank();
        }
    }

    private Event buildEvent(String eventName) {
        return new Event(UUID.randomUUID().toString(), "NORMAL", "healthcare", "omnistreamforce",
                System.currentTimeMillis(), "1.0",
                Map.of("patientId", "PAT-123456", "eventName", eventName),
                Map.of("eventName", eventName), UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }
}