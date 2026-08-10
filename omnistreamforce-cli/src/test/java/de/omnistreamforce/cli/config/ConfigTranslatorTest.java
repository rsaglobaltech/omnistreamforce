package de.omnistreamforce.cli.config;

import de.omnistreamforce.config.ConfigManager;
import de.omnistreamforce.config.OmniStreamForceConfig;
import de.omnistreamforce.engine.GenerationConfig;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.engine.PublishingMode;
import de.omnistreamforce.kafka.ClusterType;
import de.omnistreamforce.persistence.SinkType;
import de.omnistreamforce.persistence.outbox.BackpressurePolicy;
import de.omnistreamforce.routing.TopicMapping;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigTranslatorTest {

    private OmniStreamForceConfig parse(String yaml) {
        return new ConfigManager().load(new StringReader(yaml));
    }

    @Test
    void kafkaSectionBecomesAConnectionConfig() {
        var config = parse("""
                kafka:
                  clusterType: MSK
                  bootstrapServers: b-1.msk:9094
                  securityProtocol: SASL_SSL
                  saslMechanism: AWS_MSK_IAM
                  clientId: mi-cliente
                  acks: all
                  extra:
                    aws.region: eu-west-1
                domains:
                  - domain: fastfood
                """);

        var kafka = ConfigTranslator.toKafkaConnectionConfig(config);

        assertThat(kafka.clusterType()).isEqualTo(ClusterType.MSK);
        assertThat(kafka.bootstrapServers()).isEqualTo("b-1.msk:9094");
        assertThat(kafka.securityProtocol()).isEqualTo("SASL_SSL");
        assertThat(kafka.clientId()).isEqualTo("mi-cliente");
        assertThat(kafka.toProducerProps()).containsEntry("aws.region", "eu-west-1");
    }

    @Test
    void aKafkaOnlySinkHasNoPersistence() {
        var config = parse("""
                domains:
                  - domain: fastfood
                """);

        assertThat(ConfigTranslator.toPersistenceConfig(config)).isNull();
        assertThat(ConfigTranslator.toSinkConfig(config).type()).isEqualTo(SinkType.KAFKA);
        assertThat(ConfigTranslator.toSinkConfig(config).persistence()).isNull();
    }

    @Test
    void theDatabaseSectionBecomesAPersistenceConfig() {
        var config = parse("""
                domains:
                  - domain: fastfood
                    keyStrategy: ENTITY_ID
                    keyField: orderId
                sink:
                  type: DUAL
                  jdbcUrl: jdbc:postgresql://localhost:55433/osf
                  username: osf
                  password: secreto
                  outboxTable: mi_outbox
                  batchSize: 250
                  lingerMs: 40
                  writerThreads: 3
                  backpressure: DROP
                  tablePrefix: demo_
                """);

        var persistence = ConfigTranslator.toPersistenceConfig(config);

        assertThat(persistence).isNotNull();
        assertThat(persistence.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:55433/osf");
        assertThat(persistence.password()).isEqualTo("secreto");
        assertThat(persistence.outboxTable()).isEqualTo("mi_outbox");
        assertThat(persistence.batchSize()).isEqualTo(250);
        assertThat(persistence.lingerMs()).isEqualTo(40);
        assertThat(persistence.writerThreads()).isEqualTo(3);
        assertThat(persistence.backpressure()).isEqualTo(BackpressurePolicy.DROP);
        assertThat(persistence.ddlOptions().tablePrefix()).isEqualTo("demo_");

        var sink = ConfigTranslator.toSinkConfig(config);
        assertThat(sink.type()).isEqualTo(SinkType.DUAL);
        assertThat(sink.keyStrategy()).isEqualTo(KeyStrategy.ENTITY_ID);
        assertThat(sink.keyField()).isEqualTo("orderId");
        assertThat(sink.kafka()).isNotNull();
    }

    @Test
    void anUnknownBackpressurePolicyFallsBackToBlocking() {
        var config = parse("""
                domains:
                  - domain: fastfood
                sink:
                  type: DB_OUTBOX
                  jdbcUrl: jdbc:postgresql://localhost:5432/osf
                  backpressure: LO_QUE_SEA
                """);

        assertThat(ConfigTranslator.toPersistenceConfig(config).backpressure())
                .isEqualTo(BackpressurePolicy.BLOCK);
    }

    @Test
    void everyDomainKeepsItsOwnRateAndSharesTheGlobalMode() {
        var config = parse("""
                domains:
                  - domain: fastfood
                    topic: ff
                    errorTopic: ff-err
                    eventsPerSecond: 120
                    errorRate: 15
                    keyStrategy: ENTITY_ID
                    keyField: orderId
                  - domain: ecommerce
                    topic: ec
                    eventsPerSecond: 30
                    errorRate: 5
                generation:
                  publishingMode: BURST
                  durationSeconds: 30
                  burstSize: 100
                topic:
                  partitions: 6
                  replicationFactor: 2
                  autoCreate: false
                """);

        GenerationConfig fastfood = ConfigTranslator.toGenerationConfig(config, config.domains().get(0));
        GenerationConfig ecommerce = ConfigTranslator.toGenerationConfig(config, config.domains().get(1));

        assertThat(fastfood.eventsPerSecond()).isEqualTo(120);
        assertThat(fastfood.errorTopicName()).isEqualTo("ff-err");
        assertThat(fastfood.keyField()).isEqualTo("orderId");
        assertThat(ecommerce.eventsPerSecond()).isEqualTo(30);
        // sin topic de errores propio, los errores van al mismo topic
        assertThat(ecommerce.errorTopicName()).isEqualTo("ec");

        assertThat(fastfood.publishingMode()).isEqualTo(PublishingMode.BURST);
        assertThat(ecommerce.publishingMode()).isEqualTo(PublishingMode.BURST);
        assertThat(fastfood.durationSeconds()).isEqualTo(30);
        assertThat(fastfood.burstSize()).isEqualTo(100);

        TopicMapping mapping = ConfigTranslator.toTopicMapping(config, config.domains().get(0));
        assertThat(mapping.partitions()).isEqualTo(6);
        assertThat(mapping.replicationFactor()).isEqualTo((short) 2);
        assertThat(mapping.autoCreate()).isFalse();
    }
}
