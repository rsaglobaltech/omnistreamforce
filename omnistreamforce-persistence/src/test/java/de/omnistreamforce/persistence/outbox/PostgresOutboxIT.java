package de.omnistreamforce.persistence.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.fastfood.FastFoodGenerator;
import de.omnistreamforce.domain.healthcare.HealthcareGenerator;
import de.omnistreamforce.persistence.DataSourceFactory;
import de.omnistreamforce.persistence.PersistenceConfig;
import de.omnistreamforce.persistence.dialect.PostgresDialect;
import de.omnistreamforce.serializer.JsonEventSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Semantica SQL real del sink contra PostgreSQL. Se ejecuta con {@code mvn verify -Pit}.
 */
class PostgresOutboxIT {

    private static final String TOPIC = "fastfood-events";
    private static final String ERROR_TOPIC = "fastfood-errors";

    /**
     * Permite apuntar a un PostgreSQL ya levantado en vez de usar Testcontainers, util cuando
     * el entorno local no deja que Testcontainers hable con el demonio Docker:
     * {@code OSF_IT_JDBC_URL=jdbc:postgresql://localhost:5432/osf}.
     */
    private static final String EXTERNAL_URL_ENV = "OSF_IT_JDBC_URL";

    private static PostgreSQLContainer<?> postgres;
    private static DataSource dataSource;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    private final FastFoodGenerator fastfood = new FastFoodGenerator();
    private final HealthcareGenerator healthcare = new HealthcareGenerator();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void startPostgres() {
        String external = System.getenv(EXTERNAL_URL_ENV);
        if (external != null && !external.isBlank()) {
            jdbcUrl = external;
            username = orDefault(System.getenv("OSF_IT_DB_USER"), "osf");
            password = orDefault(System.getenv("OSF_IT_DB_PASSWORD"), "osf");
            return;
        }
        try {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("osf")
                    .withUsername("osf")
                    .withPassword("osf");
            postgres.start();
            jdbcUrl = postgres.getJdbcUrl();
            username = postgres.getUsername();
            password = postgres.getPassword();
        } catch (RuntimeException e) {
            postgres = null;
            Assumptions.assumeTrue(false, "Sin PostgreSQL: ni " + EXTERNAL_URL_ENV
                    + " ni Testcontainers disponibles (" + e + ")");
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @AfterAll
    static void stopPostgres() {
        if (dataSource != null) {
            DataSourceFactory.close(dataSource);
            dataSource = null;
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void cleanDatabase() throws SQLException {
        if (dataSource == null) {
            dataSource = DataSourceFactory.create(config().build(), "osf-it");
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS osf_outbox, osf_fastfood_events, osf_healthcare_events");
            connection.commit();
        }
    }

    private PersistenceConfig.Builder config() {
        return PersistenceConfig.builder(jdbcUrl)
                .username(username)
                .password(password)
                .writerThreads(2)
                .batchSize(100)
                .lingerMs(20);
    }

    private JdbcOutboxPublisher publisher(PersistenceConfig config) {
        return new JdbcOutboxPublisher(dataSource, new PostgresDialect(), config,
                new JsonEventSerializer(),
                SchemaCatalog.of(fastfood.getSchema(), healthcare.getSchema()));
    }

    @Test
    void everyEventProducesABusinessRowAndAnOutboxRow() throws Exception {
        List<Event> events = generate(500);
        try (JdbcOutboxPublisher publisher = publisher(config().build())) {
            events.forEach(event -> publisher.publish(event, TOPIC));
            publisher.flush();
        }

        assertThat(count("osf_fastfood_events")).isEqualTo(500);
        assertThat(count("osf_outbox")).isEqualTo(500);
        assertThat(count("osf_outbox WHERE status = 'PENDING'")).isEqualTo(500);
    }

    @Test
    void republishingTheSameEventsIsIdempotent() throws Exception {
        List<Event> events = generate(200);
        for (int round = 0; round < 2; round++) {
            try (JdbcOutboxPublisher publisher = publisher(config().build())) {
                events.forEach(event -> publisher.publish(event, TOPIC));
                publisher.flush();
            }
        }

        assertThat(count("osf_fastfood_events")).isEqualTo(200);
        assertThat(count("osf_outbox")).isEqualTo(200);
    }

    @Test
    void aFailingOutboxInsertRollsBackTheBusinessRowToo() throws Exception {
        // aggregateid es VARCHAR(256): una clave mas larga rompe el INSERT de outbox
        Event oversized = withKey(fastfood.generateEvent(FastFoodGenerator.ORDER_PLACED), "K".repeat(300));

        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(1).maxRetries(0).build())) {
            publisher.publish(oversized, TOPIC);
            publisher.flush();
        }

        assertThat(count("osf_fastfood_events")).isZero();
        assertThat(count("osf_outbox")).isZero();
    }

    @Test
    void typedColumnsAndOverflowAreStoredAsExpected() throws Exception {
        Event event = fastfood.generateEvent(FastFoodGenerator.ORDER_PAID);
        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(1).build())) {
            publisher.publish(event, TOPIC);
            publisher.flush();
        }

        Map<String, Object> row = singleRow("SELECT brand, store_id, total_amount, items,"
                + " payload, payload_extra, event_name, event_type, topic, message_key, event_ts"
                + " FROM osf_fastfood_events");

        assertThat(row.get("brand")).isEqualTo(event.payload().get("brand"));
        assertThat(row.get("store_id")).isEqualTo(event.payload().get("storeId"));
        assertThat(((java.math.BigDecimal) row.get("total_amount")).doubleValue())
                .isEqualTo(((Number) event.payload().get("totalAmount")).doubleValue());
        assertThat(row.get("event_name")).isEqualTo("OrderPaid");
        assertThat(row.get("event_type")).isEqualTo("NORMAL");
        assertThat(row.get("topic")).isEqualTo(TOPIC);

        JsonNode items = mapper.readTree((String) row.get("items"));
        assertThat(items.isArray()).isTrue();
        assertThat(items).isNotEmpty();

        // el payload completo se conserva y las claves no declaradas quedan aisladas
        JsonNode payload = mapper.readTree((String) row.get("payload"));
        assertThat(payload.has("orderId")).isTrue();
        assertThat(payload.has("paidAt")).isTrue();

        JsonNode extra = mapper.readTree((String) row.get("payload_extra"));
        assertThat(extra.has("paidAt")).isTrue();
        assertThat(extra.has("orderId")).isFalse();
    }

    @Test
    void theMessageKeyMatchesTheKeyChosenByTheEngine() throws Exception {
        Event event = withKey(fastfood.generateEvent(FastFoodGenerator.ORDER_PLACED), "ORD-BK-42");
        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(1).build())) {
            publisher.publish(event, TOPIC);
            publisher.flush();
        }

        assertThat(singleRow("SELECT message_key FROM osf_fastfood_events").get("message_key"))
                .isEqualTo("ORD-BK-42");
        assertThat(singleRow("SELECT aggregateid FROM osf_outbox").get("aggregateid"))
                .isEqualTo("ORD-BK-42");
    }

    @Test
    void errorEventsGoToTheErrorTopicButToTheSameDomainTable() throws Exception {
        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(10).build())) {
            for (int i = 0; i < 10; i++) {
                publisher.publish(fastfood.generateEvent(FastFoodGenerator.ORDER_PLACED), TOPIC);
            }
            for (int i = 0; i < 5; i++) {
                publisher.publish(fastfood.generateErrorEvent(), ERROR_TOPIC);
            }
            publisher.flush();
        }

        assertThat(count("osf_fastfood_events")).isEqualTo(15);
        assertThat(count("osf_fastfood_events WHERE event_type = 'ERROR'")).isEqualTo(5);
        assertThat(count("osf_outbox WHERE aggregatetype = '" + ERROR_TOPIC + "'")).isEqualTo(5);
        assertThat(count("osf_fastfood_events WHERE event_type = 'ERROR' AND severity IS NOT NULL"))
                .isEqualTo(5);
    }

    @Test
    void eachDomainGetsItsOwnTableInTheSameTransaction() throws Exception {
        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(100).build())) {
            for (int i = 0; i < 30; i++) {
                publisher.publish(fastfood.generateEvent(FastFoodGenerator.ORDER_PLACED), TOPIC);
                publisher.publish(healthcare.generateEvent("PatientAdmission"), "healthcare-events");
            }
            publisher.flush();
        }

        assertThat(count("osf_fastfood_events")).isEqualTo(30);
        assertThat(count("osf_healthcare_events")).isEqualTo(30);
        assertThat(count("osf_outbox")).isEqualTo(60);
    }

    @Test
    void schemaDriftIsVisibleWithASingleQuery() throws Exception {
        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(50).build())) {
            for (int i = 0; i < 50; i++) {
                publisher.publish(fastfood.generateEvent(FastFoodGenerator.KITCHEN_PREP_STARTED), TOPIC);
            }
            publisher.flush();
        }

        List<Map<String, Object>> drift = query(
                "SELECT k AS key, count(*) AS hits FROM osf_fastfood_events,"
                        + " LATERAL jsonb_object_keys(payload_extra) AS k GROUP BY 1 ORDER BY 2 DESC");

        assertThat(drift).isNotEmpty();
        assertThat(drift.stream().map(row -> row.get("key")).toList()).contains("stationId");
    }

    @Test
    void indexesAndOutboxTableAreCreatedAutomatically() throws Exception {
        try (JdbcOutboxPublisher publisher = publisher(config().batchSize(1).build())) {
            publisher.publish(fastfood.generateEvent(FastFoodGenerator.ORDER_PLACED), TOPIC);
            publisher.flush();
        }

        List<Map<String, Object>> indexes = query(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'osf_fastfood_events'");
        assertThat(indexes).hasSizeGreaterThanOrEqualTo(5);

        List<Map<String, Object>> outboxIndexes = query(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'osf_outbox'");
        assertThat(outboxIndexes.stream().map(row -> String.valueOf(row.get("indexname"))))
                .anyMatch(name -> name.contains("pending"));
    }

    // --- helpers -------------------------------------------------------------

    private List<Event> generate(int count) {
        List<Event> events = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            events.add(fastfood.generateEvent(FastFoodGenerator.ORDER_PLACED));
        }
        return events;
    }

    private Event withKey(Event event, String key) {
        Map<String, String> metadata = new LinkedHashMap<>(event.metadata());
        metadata.put("kafka.key", key);
        return new Event(event.eventId(), event.eventType(), event.domain(), event.source(),
                event.timestamp(), event.schemaVersion(), event.payload(), metadata,
                event.traceId(), event.correlationId());
    }

    private long count(String fromClause) throws SQLException {
        return ((Number) singleRow("SELECT count(*) AS c FROM " + fromClause).get("c")).longValue();
    }

    private Map<String, Object> singleRow(String sql) throws SQLException {
        List<Map<String, Object>> rows = query(sql);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private List<Map<String, Object>> query(String sql) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columns; i++) {
                    Object value = rs.getObject(i);
                    // jsonb llega como PGobject; para las aserciones interesa el JSON en texto
                    if (value != null && "org.postgresql.util.PGobject".equals(value.getClass().getName())) {
                        value = rs.getString(i);
                    }
                    row.put(rs.getMetaData().getColumnLabel(i), value);
                }
                rows.add(row);
            }
            connection.commit();
        }
        return rows;
    }
}
