package de.omnistreamforce.persistence.relay;

import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.kafka.ClusterType;
import de.omnistreamforce.kafka.KafkaConnectionConfig;
import de.omnistreamforce.kafka.KafkaConnectionManager;
import de.omnistreamforce.kafka.KafkaEventPublisher;
import de.omnistreamforce.persistence.DataSourceFactory;
import de.omnistreamforce.persistence.PersistenceConfig;
import de.omnistreamforce.persistence.dialect.DialectFactory;
import de.omnistreamforce.persistence.dialect.SqlDialect;
import de.omnistreamforce.serializer.SerializerFactory;

import javax.sql.DataSource;

/**
 * Arranque del relay como proceso independiente, para desarrollo y para desplegarlo aparte del
 * generador. El CLI del proyecto no se toca; cuando exista su fase, bastara con un subcomando
 * que llame a las mismas clases.
 *
 * <pre>
 * OSF_DB_URL=jdbc:postgresql://localhost:5432/osf OSF_DB_USER=osf OSF_DB_PASSWORD=osf \
 * OSF_KAFKA_BOOTSTRAP=localhost:9092 java -cp ... de.omnistreamforce.persistence.relay.RelayLauncher
 * </pre>
 */
public final class RelayLauncher {

    private RelayLauncher() {
    }

    public static void main(String[] args) {
        PersistenceConfig persistence = PersistenceConfig.fromEnv();
        String bootstrap = envOrDefault("OSF_KAFKA_BOOTSTRAP", "localhost:9092");
        String format = envOrDefault("OSF_SERIALIZER", "JSON");

        SqlDialect dialect = DialectFactory.fromJdbcUrl(persistence.jdbcUrl());
        DataSource dataSource = DataSourceFactory.create(persistence, "osf-relay");

        KafkaConnectionManager kafka = new KafkaConnectionManager(KafkaConnectionConfig.builder()
                .clusterType(ClusterType.LOCAL)
                .bootstrapServers(bootstrap)
                .clientId("osf-outbox-relay")
                .acks("all")
                .build());

        KafkaEventPublisher publisher = new KafkaEventPublisher(kafka.producer(),
                SerializerFactory.create(format), KeyStrategy.ENTITY_ID, null);

        RelayConfig config = RelayConfig.builder()
                .outboxTable(persistence.outboxTable())
                .ownsPublisher(true)
                .ownsDataSource(true)
                .build();

        OutboxRelay relay = new OutboxRelay(dataSource, dialect, publisher,
                SerializerFactory.create(format), config);

        // el shutdown hook vive aqui, en el punto de entrada, nunca dentro de la libreria
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            relay.stop();
            kafka.close();
        }, "osf-relay-shutdown"));

        relay.start();
        System.out.printf("Relay activo sobre %s -> %s%n", persistence.outboxTable(), bootstrap);
        while (relay.isRunning()) {
            try {
                Thread.sleep(5_000);
                System.out.println(relay.stats());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
