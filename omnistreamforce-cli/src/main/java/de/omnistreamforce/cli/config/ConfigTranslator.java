package de.omnistreamforce.cli.config;

import de.omnistreamforce.config.OmniStreamForceConfig;
import de.omnistreamforce.engine.GenerationConfig;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.kafka.KafkaConnectionConfig;
import de.omnistreamforce.persistence.PersistenceConfig;
import de.omnistreamforce.persistence.SinkConfig;
import de.omnistreamforce.persistence.SinkType;
import de.omnistreamforce.persistence.ddl.DdlOptions;
import de.omnistreamforce.persistence.outbox.BackpressurePolicy;
import de.omnistreamforce.routing.TopicMapping;

import java.util.Locale;

/**
 * Traduce la configuracion YAML, que es deliberadamente plana y vive en el nucleo, a los objetos
 * de los modulos de Kafka y persistencia. Esta traduccion vive en el CLI para que el nucleo no
 * tenga que conocer JDBC.
 */
public final class ConfigTranslator {

    private ConfigTranslator() {
    }

    public static KafkaConnectionConfig toKafkaConnectionConfig(OmniStreamForceConfig config) {
        OmniStreamForceConfig.KafkaSection kafka = config.kafka();
        KafkaConnectionConfig.Builder builder = KafkaConnectionConfig.builder()
                .clusterType(kafka.clusterType())
                .bootstrapServers(kafka.bootstrapServers())
                .securityProtocol(kafka.securityProtocol())
                .saslMechanism(kafka.saslMechanism())
                .saslJaasConfig(kafka.saslJaasConfig())
                .clientId(kafka.clientId())
                .acks(kafka.acks());
        kafka.extra().forEach(builder::extraProp);
        return builder.build();
    }

    public static PersistenceConfig toPersistenceConfig(OmniStreamForceConfig config) {
        OmniStreamForceConfig.SinkSection sink = config.sink();
        if (!sink.usesDatabase()) {
            return null;
        }
        return PersistenceConfig.builder(sink.jdbcUrl())
                .username(sink.username())
                .password(sink.password())
                .outboxTable(sink.outboxTable())
                .batchSize(sink.batchSize())
                .lingerMs(sink.lingerMs())
                .writerThreads(sink.writerThreads())
                .backpressure(backpressure(sink.backpressure()))
                .writeBusinessTable(sink.writeBusinessTable())
                .ddlOptions(new DdlOptions(sink.tablePrefix(), true, false, false, true))
                .build();
    }

    /**
     * La estrategia de clave es por dominio, pero el publisher es uno solo: se toma la del primer
     * dominio como estrategia global, que es la que aplica cuando el motor no precalcula la clave.
     */
    public static SinkConfig toSinkConfig(OmniStreamForceConfig config) {
        KeyStrategy keyStrategy = KeyStrategy.RANDOM;
        String keyField = null;
        if (!config.domains().isEmpty()) {
            keyStrategy = config.domains().get(0).keyStrategy();
            keyField = config.domains().get(0).keyField();
        }
        return SinkConfig.builder(sinkType(config.sink().type()))
                .kafka(config.sink().usesKafka() ? toKafkaConnectionConfig(config) : null)
                .persistence(toPersistenceConfig(config))
                .keyStrategy(keyStrategy)
                .keyField(keyField)
                .serializerFormat(config.serialization().format())
                .build();
    }

    public static GenerationConfig toGenerationConfig(OmniStreamForceConfig config,
                                                      OmniStreamForceConfig.DomainMapping domain) {
        return new GenerationConfig(
                domain.domain(),
                domain.topic(),
                domain.resolvedErrorTopic(),
                domain.eventsPerSecond(),
                domain.errorRate(),
                config.generation().publishingMode(),
                config.generation().durationSeconds(),
                domain.keyStrategy(),
                domain.keyField(),
                config.generation().burstSize(),
                config.generation().rampTargetEPS());
    }

    public static TopicMapping toTopicMapping(OmniStreamForceConfig config,
                                              OmniStreamForceConfig.DomainMapping domain) {
        return new TopicMapping(
                domain.domain(),
                domain.topic(),
                domain.resolvedErrorTopic(),
                config.topic().partitions(),
                config.topic().replicationFactor(),
                config.topic().autoCreate());
    }

    private static SinkType sinkType(String value) {
        return SinkType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    private static BackpressurePolicy backpressure(String value) {
        try {
            return BackpressurePolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BackpressurePolicy.BLOCK;
        }
    }
}
