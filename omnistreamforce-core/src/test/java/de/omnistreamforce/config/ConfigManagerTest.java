package de.omnistreamforce.config;

import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.engine.PublishingMode;
import de.omnistreamforce.kafka.ClusterType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigManagerTest {

    private final ConfigManager manager = new ConfigManager(
            new PlaceholderResolver(name -> switch (name) {
                case "KAFKA_BOOTSTRAP_SERVERS" -> "broker-1:9092";
                case "OSF_DB_PASSWORD" -> "secreto";
                default -> null;
            }));

    private OmniStreamForceConfig parse(String yaml) {
        return manager.load(new StringReader(yaml));
    }

    @Test
    void aCompleteFileIsMappedToTheModel() {
        OmniStreamForceConfig config = parse("""
                kafka:
                  clusterType: MSK
                  bootstrapServers: b-1.msk:9094
                  securityProtocol: SASL_SSL
                  acks: all
                  extra:
                    aws.region: eu-west-1
                domains:
                  - domain: fastfood
                    topic: fastfood-events
                    errorTopic: fastfood-errors
                    eventsPerSecond: 120
                    errorRate: 15.5
                    keyStrategy: ENTITY_ID
                    keyField: orderId
                generation:
                  publishingMode: BURST
                  durationSeconds: 60
                  burstSize: 200
                serialization:
                  format: AVRO
                  prettyPrint: true
                sink:
                  type: DUAL
                  jdbcUrl: jdbc:postgresql://localhost:5432/osf
                topic:
                  partitions: 6
                  replicationFactor: 3
                  autoCreate: false
                """);

        assertThat(config.kafka().clusterType()).isEqualTo(ClusterType.MSK);
        assertThat(config.kafka().bootstrapServers()).isEqualTo("b-1.msk:9094");
        assertThat(config.kafka().extra()).containsEntry("aws.region", "eu-west-1");

        assertThat(config.domains()).hasSize(1);
        OmniStreamForceConfig.DomainMapping domain = config.domains().get(0);
        assertThat(domain.domain()).isEqualTo("fastfood");
        assertThat(domain.eventsPerSecond()).isEqualTo(120);
        assertThat(domain.errorRate()).isEqualTo(15.5);
        assertThat(domain.keyStrategy()).isEqualTo(KeyStrategy.ENTITY_ID);
        assertThat(domain.hasSeparateErrorTopic()).isTrue();

        assertThat(config.generation().publishingMode()).isEqualTo(PublishingMode.BURST);
        assertThat(config.generation().durationSeconds()).isEqualTo(60);
        assertThat(config.serialization().format()).isEqualTo("AVRO");
        assertThat(config.sink().type()).isEqualTo("DUAL");
        assertThat(config.topic().partitions()).isEqualTo(6);
        assertThat(config.topic().replicationFactor()).isEqualTo((short) 3);
    }

    @Test
    void missingSectionsFallBackToDefaults() {
        OmniStreamForceConfig config = parse("""
                domains:
                  - domain: fastfood
                """);

        assertThat(config.kafka().bootstrapServers()).isEqualTo("localhost:9092");
        assertThat(config.kafka().clusterType()).isEqualTo(ClusterType.LOCAL);
        assertThat(config.generation().publishingMode()).isEqualTo(PublishingMode.STEADY);
        assertThat(config.serialization().format()).isEqualTo("JSON");
        assertThat(config.sink().type()).isEqualTo("KAFKA");
        // el topic se deduce del nombre del dominio
        assertThat(config.domains().get(0).topic()).isEqualTo("fastfood-events");
        assertThat(config.domains().get(0).eventsPerSecond()).isEqualTo(50);
    }

    @Test
    void enumsAreCaseInsensitiveAndAcceptHyphens() {
        OmniStreamForceConfig config = parse("""
                kafka:
                  clusterType: msk
                domains:
                  - domain: fastfood
                    keyStrategy: round-robin
                generation:
                  publishingMode: ramp
                """);

        assertThat(config.kafka().clusterType()).isEqualTo(ClusterType.MSK);
        assertThat(config.domains().get(0).keyStrategy()).isEqualTo(KeyStrategy.ROUND_ROBIN);
        assertThat(config.generation().publishingMode()).isEqualTo(PublishingMode.RAMP);
    }

    @Test
    void anUnknownEnumValueSaysWhatIsAccepted() {
        assertThatThrownBy(() -> parse("""
                generation:
                  publishingMode: TURBO
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TURBO")
                .hasMessageContaining("STEADY");
    }

    @Test
    void placeholdersAreResolvedFromTheEnvironment() {
        OmniStreamForceConfig config = parse("""
                kafka:
                  bootstrapServers: ${KAFKA_BOOTSTRAP_SERVERS}
                sink:
                  type: DB_OUTBOX
                  jdbcUrl: jdbc:postgresql://localhost:5432/osf
                  password: ${OSF_DB_PASSWORD}
                  username: ${OSF_DB_USER:-osf}
                domains:
                  - domain: fastfood
                """);

        assertThat(config.kafka().bootstrapServers()).isEqualTo("broker-1:9092");
        assertThat(config.sink().password()).isEqualTo("secreto");
        assertThat(config.sink().username()).isEqualTo("osf");
    }

    @Test
    void emptyOrMalformedFilesAreReported() {
        assertThat(manager.load(new StringReader(""))).isEqualTo(OmniStreamForceConfig.defaults());
        assertThatThrownBy(() -> parse("- solo\n- una lista"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mapa YAML");
        assertThatThrownBy(() -> parse("kafka: no-soy-un-mapa"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'kafka'");
    }

    @Test
    void validationCatchesTheUsualMistakes() {
        List<String> errors = manager.validate(parse("""
                sink:
                  type: DUAL
                serialization:
                  format: XML
                """));

        assertThat(errors).anyMatch(e -> e.contains("No hay dominios"));
        assertThat(errors).anyMatch(e -> e.contains("sink.jdbcUrl"));
        assertThat(errors).anyMatch(e -> e.contains("XML"));
    }

    @Test
    void entityIdWithoutKeyFieldIsRejected() {
        List<String> errors = manager.validate(parse("""
                domains:
                  - domain: fastfood
                    keyStrategy: ENTITY_ID
                """));

        assertThat(errors).anyMatch(e -> e.contains("keyField"));
    }

    @Test
    void duplicatedDomainsAreRejected() {
        List<String> errors = manager.validate(parse("""
                domains:
                  - domain: fastfood
                    topic: a
                  - domain: fastfood
                    topic: b
                """));

        assertThat(errors).anyMatch(e -> e.contains("dos veces"));
    }

    @Test
    void theOutboxRequiresJsonBecauseThePayloadIsStoredAsJsonb() {
        List<String> errors = manager.validate(parse("""
                domains:
                  - domain: fastfood
                sink:
                  type: DB_OUTBOX
                  jdbcUrl: jdbc:postgresql://localhost:5432/osf
                serialization:
                  format: PROTOBUF
                """));

        assertThat(errors).anyMatch(e -> e.contains("requiere formato JSON"));
    }

    @Test
    void aValidConfigurationHasNoErrors() {
        assertThat(manager.validate(parse("""
                domains:
                  - domain: fastfood
                    topic: fastfood-events
                    keyStrategy: ENTITY_ID
                    keyField: orderId
                """))).isEmpty();
    }

    @Test
    void overrideWinsOnlyWhereItBringsSomething() {
        OmniStreamForceConfig base = parse("""
                kafka:
                  bootstrapServers: base:9092
                domains:
                  - domain: fastfood
                    eventsPerSecond: 10
                serialization:
                  format: AVRO
                """);
        OmniStreamForceConfig override = parse("""
                kafka:
                  bootstrapServers: override:9092
                """);

        OmniStreamForceConfig merged = manager.merge(base, override);

        assertThat(merged.kafka().bootstrapServers()).isEqualTo("override:9092");
        assertThat(merged.domains()).hasSize(1);          // el override no traia dominios
        assertThat(merged.serialization().format()).isEqualTo("AVRO");
    }

    @Test
    void savingAndLoadingBackKeepsTheConfiguration(@TempDir Path dir) {
        OmniStreamForceConfig original = parse("""
                kafka:
                  clusterType: LOCAL
                  bootstrapServers: localhost:9092
                domains:
                  - domain: fastfood
                    topic: fastfood-events
                    errorTopic: fastfood-errors
                    eventsPerSecond: 42
                    errorRate: 7.5
                    keyStrategy: ENTITY_ID
                    keyField: orderId
                sink:
                  type: DB_OUTBOX
                  jdbcUrl: jdbc:postgresql://localhost:5432/osf
                """);

        Path file = dir.resolve("perfil.yaml");
        manager.save(original, file);
        assertThat(Files.exists(file)).isTrue();

        OmniStreamForceConfig reloaded = manager.load(file);

        assertThat(reloaded.domains()).isEqualTo(original.domains());
        assertThat(reloaded.kafka().bootstrapServers()).isEqualTo("localhost:9092");
        assertThat(reloaded.sink().jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/osf");
    }
}
