package de.omnistreamforce.persistence;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.fastfood.FastFoodGenerator;
import de.omnistreamforce.engine.CompositePublisher;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.kafka.ClusterType;
import de.omnistreamforce.kafka.KafkaConnectionConfig;
import de.omnistreamforce.persistence.outbox.SchemaCatalog;
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
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Modo DUAL: el mismo evento sale por Kafka y se guarda en base de datos, sin que el motor
 * sepa nada. Contra PostgreSQL y Kafka reales.
 */
class DualSinkIT {

    private static PostgreSQLContainer<?> postgres;
    private static KafkaContainer kafkaContainer;
    private static DataSource verificationDataSource;
    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static String bootstrap;

    private final FastFoodGenerator generator = new FastFoodGenerator();

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
        if (verificationDataSource != null) {
            DataSourceFactory.close(verificationDataSource);
            verificationDataSource = null;
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
        if (verificationDataSource == null) {
            verificationDataSource = DataSourceFactory.create(persistence(), "osf-dual-it");
        }
        try (Connection connection = verificationDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS osf_outbox, osf_fastfood_events");
            connection.commit();
        }
    }

    private PersistenceConfig persistence() {
        return PersistenceConfig.builder(jdbcUrl)
                .username(username)
                .password(password)
                .writerThreads(2)
                .batchSize(100)
                .lingerMs(10)
                .build();
    }

    private SinkConfig.Builder sink(SinkType type) {
        return SinkConfig.builder(type)
                .kafka(KafkaConnectionConfig.builder()
                        .clusterType(ClusterType.LOCAL)
                        .bootstrapServers(bootstrap)
                        .clientId("osf-dual-it")
                        .acks("all")
                        .lingerMs(5)
                        .build())
                .persistence(persistence())
                .keyStrategy(KeyStrategy.ENTITY_ID)
                .keyField("orderId")
                .schemaCatalog(SchemaCatalog.of(generator.getSchema()));
    }

    @Test
    void dualSinkWritesToKafkaAndToTheDatabase() throws Exception {
        String topic = "dual-it-" + UUID.randomUUID();

        try (PublisherFactory.ManagedPublisher publisher = PublisherFactory.create(
                sink(SinkType.DUAL).dualPolicy(CompositePublisher.FailurePolicy.FAIL_FAST).build())) {
            for (int i = 0; i < 500; i++) {
                publisher.publish(event(), topic);
            }
            publisher.flush();
        }

        assertThat(consume(topic, 500)).hasSize(500);
        assertThat(count("osf_fastfood_events")).isEqualTo(500);
        assertThat(count("osf_outbox")).isEqualTo(500);
    }

    @Test
    void kafkaOnlySinkLeavesTheDatabaseUntouched() throws Exception {
        String topic = "dual-it-" + UUID.randomUUID();

        try (PublisherFactory.ManagedPublisher publisher =
                     PublisherFactory.create(sink(SinkType.KAFKA).persistence(null).build())) {
            for (int i = 0; i < 50; i++) {
                publisher.publish(event(), topic);
            }
            publisher.flush();

            assertThat(publisher.outboxPublisher()).isEmpty();
            assertThat(publisher.kafkaPublisher()).isPresent();
        }

        assertThat(consume(topic, 50)).hasSize(50);
        assertThat(tableExists("osf_fastfood_events")).isFalse();
    }

    @Test
    void databaseOnlySinkPublishesNothingToKafka() throws Exception {
        String topic = "dual-it-" + UUID.randomUUID();

        try (PublisherFactory.ManagedPublisher publisher =
                     PublisherFactory.create(sink(SinkType.DB_OUTBOX).kafka(null).build())) {
            for (int i = 0; i < 100; i++) {
                publisher.publish(event(), topic);
            }
            publisher.flush();

            assertThat(publisher.kafkaPublisher()).isEmpty();
        }

        assertThat(count("osf_fastfood_events")).isEqualTo(100);
        assertThat(count("osf_outbox WHERE status = 'PENDING'")).isEqualTo(100);
        // nadie publico: el topic no existe o esta vacio
        assertThat(consume(topic, 1)).isEmpty();
    }

    @Test
    void theSameKeyIsUsedOnBothPaths() throws Exception {
        String topic = "dual-it-" + UUID.randomUUID();
        try (PublisherFactory.ManagedPublisher publisher =
                     PublisherFactory.create(sink(SinkType.DUAL).build())) {
            publisher.publish(event(), topic);
            publisher.flush();
        }

        List<ConsumerRecord<String, String>> records = consume(topic, 1);
        assertThat(records).hasSize(1);
        String kafkaKey = records.get(0).key();

        try (Connection connection = verificationDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT message_key FROM osf_fastfood_events")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo(kafkaKey);
            connection.commit();
        }
    }

    // --- helpers -------------------------------------------------------------

    private Event event() {
        return generator.generateEvent(FastFoodGenerator.ORDER_PLACED);
    }

    private long count(String fromClause) throws SQLException {
        try (Connection connection = verificationDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM " + fromClause)) {
            rs.next();
            long value = rs.getLong(1);
            connection.commit();
            return value;
        }
    }

    private boolean tableExists(String table) throws SQLException {
        try (Connection connection = verificationDataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT to_regclass('" + table + "') IS NOT NULL")) {
            rs.next();
            boolean exists = rs.getBoolean(1);
            connection.commit();
            return exists;
        }
    }

    private List<ConsumerRecord<String, String>> consume(String topic, int expected) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrap);
        props.put("group.id", "osf-dual-it-" + UUID.randomUUID());
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());

        List<ConsumerRecord<String, String>> collected = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 20_000;
            while (collected.size() < expected && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                records.forEach(collected::add);
            }
        }
        return collected;
    }
}
