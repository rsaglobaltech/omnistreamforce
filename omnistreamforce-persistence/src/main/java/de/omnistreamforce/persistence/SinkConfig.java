package de.omnistreamforce.persistence;

import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.engine.CompositePublisher;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.kafka.KafkaConnectionConfig;

import java.util.function.Function;

/**
 * Que destino usar y con que parametros. Lo consume {@link PublisherFactory}.
 *
 * @param serializerFormat JSON, AVRO o PROTOBUF; se resuelve con SerializerFactory
 * @param schemaCatalog    resuelve el EventSchema de cada dominio para derivar su tabla;
 *                         si es null se usa el descubrimiento por ServiceLoader
 */
public record SinkConfig(
        SinkType type,
        KafkaConnectionConfig kafka,
        PersistenceConfig persistence,
        KeyStrategy keyStrategy,
        String keyField,
        String serializerFormat,
        int kafkaMaxRetries,
        long kafkaRetryBackoffMs,
        CompositePublisher.FailurePolicy dualPolicy,
        Function<String, EventSchema> schemaCatalog
) {

    public SinkConfig {
        type = type == null ? SinkType.KAFKA : type;
        keyStrategy = keyStrategy == null ? KeyStrategy.RANDOM : keyStrategy;
        serializerFormat = serializerFormat == null || serializerFormat.isBlank()
                ? "JSON" : serializerFormat;
        dualPolicy = dualPolicy == null ? CompositePublisher.FailurePolicy.CONTINUE : dualPolicy;
        if (type != SinkType.DB_OUTBOX && kafka == null) {
            throw new IllegalArgumentException("El sink " + type + " requiere configuracion de Kafka");
        }
        if (type != SinkType.KAFKA && persistence == null) {
            throw new IllegalArgumentException("El sink " + type + " requiere configuracion de persistencia");
        }
    }

    public static Builder builder(SinkType type) {
        return new Builder(type);
    }

    public static final class Builder {
        private final SinkType type;
        private KafkaConnectionConfig kafka;
        private PersistenceConfig persistence;
        private KeyStrategy keyStrategy = KeyStrategy.RANDOM;
        private String keyField;
        private String serializerFormat = "JSON";
        private int kafkaMaxRetries = 0;
        private long kafkaRetryBackoffMs = 0;
        private CompositePublisher.FailurePolicy dualPolicy = CompositePublisher.FailurePolicy.CONTINUE;
        private Function<String, EventSchema> schemaCatalog;

        private Builder(SinkType type) {
            this.type = type;
        }

        public Builder kafka(KafkaConnectionConfig v) { this.kafka = v; return this; }
        public Builder persistence(PersistenceConfig v) { this.persistence = v; return this; }
        public Builder keyStrategy(KeyStrategy v) { this.keyStrategy = v; return this; }
        public Builder keyField(String v) { this.keyField = v; return this; }
        public Builder serializerFormat(String v) { this.serializerFormat = v; return this; }
        public Builder kafkaMaxRetries(int v) { this.kafkaMaxRetries = v; return this; }
        public Builder kafkaRetryBackoffMs(long v) { this.kafkaRetryBackoffMs = v; return this; }
        public Builder dualPolicy(CompositePublisher.FailurePolicy v) { this.dualPolicy = v; return this; }
        public Builder schemaCatalog(Function<String, EventSchema> v) { this.schemaCatalog = v; return this; }

        public SinkConfig build() {
            return new SinkConfig(type, kafka, persistence, keyStrategy, keyField, serializerFormat,
                    kafkaMaxRetries, kafkaRetryBackoffMs, dualPolicy, schemaCatalog);
        }
    }
}
