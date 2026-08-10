package de.omnistreamforce.persistence.relay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.fastfood.FastFoodGenerator;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.kafka.ClusterType;
import de.omnistreamforce.kafka.KafkaConnectionConfig;
import de.omnistreamforce.kafka.KafkaConnectionManager;
import de.omnistreamforce.kafka.KafkaEventPublisher;
import de.omnistreamforce.persistence.DataSourceFactory;
import de.omnistreamforce.persistence.PersistenceConfig;
import de.omnistreamforce.persistence.dialect.PostgresDialect;
import de.omnistreamforce.persistence.outbox.JdbcOutboxPublisher;
import de.omnistreamforce.persistence.outbox.SchemaCatalog;
import de.omnistreamforce.serializer.JsonEventSerializer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Relay end-to-end: outbox en PostgreSQL real -> Kafka real.
 * <p>
 * Usa {@code OSF_IT_JDBC_URL} y {@code OSF_IT_KAFKA_BOOTSTRAP} si estan definidas; si no,
 * levanta contenedores con Testcontainers y, si tampoco es posible, se omite.
 */
class OutboxRelayIT {

    private static PostgreSQLContainer<?> postgres;
    private static KafkaContainer kafkaContainer;
    private static DataSource dataSource;
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static String bootstrap;

    private final FastFoodGenerator generator = new FastFoodGenerator();
    private final JsonEventSerializer serializer = new JsonEventSerializer();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void startInfrastructure() {
        String externalDb = System.getenv("OSF_IT_JDBC_URL");
        String externalKafka = System.getenv("OSF_IT_KAFKA_BOOTSTRAP");
        try {
            if (externalDb != null && !externalDb.isBlank()) {
                jdbcUrl = externalDb;
                username = orDefault(System.getenv("OSF_IT_DB_USER"), "osf");
                password = orDefault(System.getenv("OSF_IT_DB_PASSWORD"), "osf");
            } else {
                postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("osf").withUsername("osf").withPassword("osf");
                postgres.start();
                jdbcUrl = postgres.getJdbcUrl();
                username = postgres.getUsername();
                password = postgres.getPassword();
            }
            if (externalKafka != null && !externalKafka.isBlank()) {
                bootstrap = externalKafka;
            } else {
                kafkaContainer = new KafkaContainer("apache/kafka:3.8.0");
                kafkaContainer.start();
                bootstrap = kafkaContainer.getBootstrapServers();
            }
        } catch (RuntimeException e) {
            Assumptions.assumeTrue(false, "Sin PostgreSQL/Kafka para el test de integracion: " + e);
        }
    }

    @AfterAll
    static void stopInfrastructure() {
        if (dataSource != null) {
            DataSourceFactory.close(dataSource);
            dataSource = null;
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (kafkaContainer != null) {
            kafkaContainer.stop();
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @BeforeEach
    void cleanDatabase() throws SQLException {
        if (dataSource == null) {
            dataSource = DataSourceFactory.create(persistenceConfig().build(), "osf-relay-it");
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS osf_outbox, osf_fastfood_events");
            connection.commit();
        }
    }

    private PersistenceConfig.Builder persistenceConfig() {
        return PersistenceConfig.builder(jdbcUrl)
                .username(username)
                .password(password)
                .writerThreads(2)
                .batchSize(100)
                .lingerMs(10);
    }

    private KafkaConnectionManager kafka(String clientId) {
        return new KafkaConnectionManager(KafkaConnectionConfig.builder()
                .clusterType(ClusterType.LOCAL)
                .bootstrapServers(bootstrap)
                .clientId(clientId)
                .acks("all")
                .lingerMs(5)
                .build());
    }

    /** Llena el outbox sin publicar a Kafka: el relay es quien debe hacerlo. */
    private void fillOutbox(int count, String topic, boolean errors) {
        try (JdbcOutboxPublisher sink = new JdbcOutboxPublisher(dataSource, new PostgresDialect(),
                persistenceConfig().build(), serializer, SchemaCatalog.of(generator.getSchema()))) {
            for (int i = 0; i < count; i++) {
                Event event = errors
                        ? generator.generateErrorEvent()
                        : generator.generateEvent(FastFoodGenerator.ORDER_PLACED);
                sink.publish(withKey(event, "ORD-" + i), topic);
            }
            sink.flush();
        }
    }

    private Event withKey(Event event, String key) {
        Map<String, String> metadata = new LinkedHashMap<>(event.metadata());
        metadata.put("kafka.key", key);
        return new Event(event.eventId(), event.eventType(), event.domain(), event.source(),
                event.timestamp(), event.schemaVersion(), event.payload(), metadata,
                event.traceId(), event.correlationId());
    }

    @Test
    void everyPendingRowEndsUpInKafkaAndIsMarkedPublished() throws Exception {
        String topic = "relay-it-" + UUID.randomUUID();
        fillOutbox(300, topic, false);
        assertThat(count("osf_outbox WHERE status = 'PENDING'")).isEqualTo(300);

        try (KafkaConnectionManager kafka = kafka("relay-it")) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(kafka.producer(), serializer,
                    KeyStrategy.ENTITY_ID, "orderId");
            try (OutboxRelay relay = relay(publisher, RelayConfig.builder().batchSize(100))) {
                relay.start();
                waitUntilDrained(relay, 300);
            }
        }

        assertThat(count("osf_outbox WHERE status = 'PENDING'")).isZero();
        assertThat(count("osf_outbox WHERE status = 'PUBLISHED'")).isEqualTo(300);

        List<ConsumerRecord<String, String>> records = consume(topic, 300);
        assertThat(records).hasSize(300);
    }

    @Test
    void twoRelaysOverTheSameTableDoNotDuplicateAnything() throws Exception {
        String topic = "relay-it-" + UUID.randomUUID();
        fillOutbox(400, topic, false);

        try (KafkaConnectionManager kafka = kafka("relay-it-dual")) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(kafka.producer(), serializer,
                    KeyStrategy.ENTITY_ID, "orderId");
            // FOR UPDATE SKIP LOCKED: cada relay se salta las filas que el otro tiene bloqueadas
            try (OutboxRelay first = relay(publisher, RelayConfig.builder().batchSize(50));
                 OutboxRelay second = relay(publisher, RelayConfig.builder().batchSize(50))) {
                first.start();
                second.start();
                waitUntilPending(0, Duration.ofSeconds(60));
            }
        }

        List<ConsumerRecord<String, String>> records = consume(topic, 400);
        assertThat(records).hasSize(400);
        assertThat(records.stream().map(ConsumerRecord::key).distinct().count()).isEqualTo(400);
    }

    @Test
    void theRecordKeyIsTheAggregateIdStoredInTheOutbox() throws Exception {
        String topic = "relay-it-" + UUID.randomUUID();
        fillOutbox(20, topic, false);

        try (KafkaConnectionManager kafka = kafka("relay-it-key")) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(kafka.producer(), serializer,
                    KeyStrategy.RANDOM, null);   // aun con RANDOM debe ganar la clave del outbox
            try (OutboxRelay relay = relay(publisher, RelayConfig.builder())) {
                relay.start();
                waitUntilDrained(relay, 20);
            }
        }

        List<ConsumerRecord<String, String>> records = consume(topic, 20);
        assertThat(records.stream().map(ConsumerRecord::key))
                .allMatch(key -> key != null && key.startsWith("ORD-"));

        for (ConsumerRecord<String, String> record : records) {
            JsonNode event = mapper.readTree(record.value());
            assertThat(event.path("domain").asText()).isEqualTo("fastfood");
        }
    }

    @Test
    void errorEventsKeepTheirOwnTopic() throws Exception {
        String normalTopic = "relay-it-" + UUID.randomUUID();
        String errorTopic = normalTopic + "-errors";
        fillOutbox(20, normalTopic, false);
        fillOutbox(10, errorTopic, true);

        try (KafkaConnectionManager kafka = kafka("relay-it-routing")) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(kafka.producer(), serializer,
                    KeyStrategy.ENTITY_ID, "orderId");
            try (OutboxRelay relay = relay(publisher, RelayConfig.builder())) {
                relay.start();
                waitUntilDrained(relay, 30);
            }
        }

        assertThat(consume(normalTopic, 20)).hasSize(20);
        List<ConsumerRecord<String, String>> errors = consume(errorTopic, 10);
        assertThat(errors).hasSize(10);
        for (ConsumerRecord<String, String> record : errors) {
            assertThat(mapper.readTree(record.value()).path("eventType").asText()).isEqualTo("ERROR");
        }
    }

    @Test
    void deleteAfterPublishEmptiesTheOutbox() throws Exception {
        String topic = "relay-it-" + UUID.randomUUID();
        fillOutbox(50, topic, false);

        try (KafkaConnectionManager kafka = kafka("relay-it-delete")) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(kafka.producer(), serializer,
                    KeyStrategy.ENTITY_ID, "orderId");
            try (OutboxRelay relay = relay(publisher,
                    RelayConfig.builder().deleteAfterPublish(true))) {
                relay.start();
                waitUntilDrained(relay, 50);
            }
        }

        assertThat(count("osf_outbox")).isZero();
        assertThat(consume(topic, 50)).hasSize(50);
    }

    @Test
    void lagIsReportedWhileRowsAreStillPending() throws Exception {
        String topic = "relay-it-" + UUID.randomUUID();
        fillOutbox(100, topic, false);

        try (KafkaConnectionManager kafka = kafka("relay-it-lag")) {
            KafkaEventPublisher publisher = new KafkaEventPublisher(kafka.producer(), serializer,
                    KeyStrategy.ENTITY_ID, "orderId");
            try (OutboxRelay relay = relay(publisher,
                    RelayConfig.builder().lagSampleIntervalMs(200))) {
                relay.start();
                waitUntilDrained(relay, 100);
                Thread.sleep(500);
                assertThat(relay.stats().pendingCount()).isZero();
                assertThat(relay.stats().published()).isEqualTo(100);
                assertThat(relay.stats().avgPublishLatencyMs()).isPositive();
            }
        }
    }

    // --- helpers -------------------------------------------------------------

    private OutboxRelay relay(KafkaEventPublisher publisher, RelayConfig.Builder builder) {
        return new OutboxRelay(dataSource, new PostgresDialect(), publisher, serializer,
                builder.pollIntervalMs(50).ownsPublisher(false).ownsDataSource(false).build());
    }

    private void waitUntilDrained(OutboxRelay relay, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            if (relay.stats().published() >= expected) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("El relay no publico " + expected + " filas a tiempo: " + relay.stats());
    }

    private void waitUntilPending(long expected, Duration timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (count("osf_outbox WHERE status = 'PENDING'") == expected) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Siguen quedando filas PENDING");
    }

    private List<ConsumerRecord<String, String>> consume(String topic, int expected) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrap);
        props.put("group.id", "osf-relay-it-" + UUID.randomUUID());
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());

        List<ConsumerRecord<String, String>> collected = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 30_000;
            while (collected.size() < expected && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(collected::add);
            }
        }
        return collected;
    }

    private long count(String fromClause) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM " + fromClause)) {
            rs.next();
            long value = rs.getLong(1);
            connection.commit();
            return value;
        }
    }
}
