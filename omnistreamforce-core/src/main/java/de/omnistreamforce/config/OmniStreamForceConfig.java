package de.omnistreamforce.config;

import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.engine.PublishingMode;
import de.omnistreamforce.kafka.ClusterType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuracion completa de una ejecucion, cargada desde YAML.
 * <p>
 * Las secciones son deliberadamente planas y sin tipos de otros modulos: el nucleo no debe
 * arrastrar JDBC. La seccion {@link SinkSection} describe la base de datos con valores simples y
 * es el CLI quien la traduce a la configuracion del modulo de persistencia.
 */
public record OmniStreamForceConfig(
        KafkaSection kafka,
        List<DomainMapping> domains,
        GenerationSection generation,
        SerializationSection serialization,
        SinkSection sink,
        TopicSection topic
) {

    public OmniStreamForceConfig {
        kafka = kafka == null ? KafkaSection.defaults() : kafka;
        domains = domains == null ? List.of() : List.copyOf(domains);
        generation = generation == null ? GenerationSection.defaults() : generation;
        serialization = serialization == null ? SerializationSection.defaults() : serialization;
        sink = sink == null ? SinkSection.defaults() : sink;
        topic = topic == null ? TopicSection.defaults() : topic;
    }

    public static OmniStreamForceConfig defaults() {
        return new OmniStreamForceConfig(null, null, null, null, null, null);
    }

    public int totalEventsPerSecond() {
        return domains.stream().mapToInt(DomainMapping::eventsPerSecond).sum();
    }

    /** Conexion al cluster. {@code extra} permite cualquier propiedad del cliente de Kafka. */
    public record KafkaSection(
            ClusterType clusterType,
            String bootstrapServers,
            String securityProtocol,
            String saslMechanism,
            String saslJaasConfig,
            String clientId,
            String acks,
            Map<String, String> extra
    ) {
        public KafkaSection {
            clusterType = clusterType == null ? ClusterType.LOCAL : clusterType;
            bootstrapServers = blankTo(bootstrapServers, "localhost:9092");
            securityProtocol = blankTo(securityProtocol, "PLAINTEXT");
            clientId = blankTo(clientId, "omnistreamforce");
            acks = blankTo(acks, "all");
            extra = extra == null ? Map.of() : Map.copyOf(extra);
        }

        public static KafkaSection defaults() {
            return new KafkaSection(null, null, null, null, null, null, null, null);
        }
    }

    /** Un dominio y su destino. */
    public record DomainMapping(
            String domain,
            String topic,
            String errorTopic,
            int eventsPerSecond,
            double errorRate,
            KeyStrategy keyStrategy,
            String keyField
    ) {
        public DomainMapping {
            topic = blankTo(topic, domain == null ? null : domain + "-events");
            eventsPerSecond = eventsPerSecond <= 0 ? 50 : eventsPerSecond;
            errorRate = errorRate < 0 ? 0 : Math.min(errorRate, 100);
            keyStrategy = keyStrategy == null ? KeyStrategy.RANDOM : keyStrategy;
        }

        public boolean hasSeparateErrorTopic() {
            return errorTopic != null && !errorTopic.isBlank() && !errorTopic.equals(topic);
        }

        /** Topic al que van los errores: el propio si no se configuro uno aparte. */
        public String resolvedErrorTopic() {
            return hasSeparateErrorTopic() ? errorTopic : topic;
        }
    }

    public record GenerationSection(
            PublishingMode publishingMode,
            long durationSeconds,
            int burstSize,
            int rampTargetEPS
    ) {
        public GenerationSection {
            publishingMode = publishingMode == null ? PublishingMode.STEADY : publishingMode;
            durationSeconds = Math.max(0, durationSeconds);
        }

        public static GenerationSection defaults() {
            return new GenerationSection(null, 0, 0, 0);
        }
    }

    public record SerializationSection(String format, boolean prettyPrint, String schemaRegistryUrl) {
        public SerializationSection {
            format = blankTo(format, "JSON");
        }

        public static SerializationSection defaults() {
            return new SerializationSection(null, false, null);
        }
    }

    /**
     * Destino de los eventos. {@code type} admite KAFKA, DB_OUTBOX o DUAL; los campos de base de
     * datos solo se usan en los dos ultimos.
     */
    public record SinkSection(
            String type,
            String jdbcUrl,
            String username,
            String password,
            String outboxTable,
            int batchSize,
            long lingerMs,
            int writerThreads,
            String backpressure,
            boolean writeBusinessTable,
            String tablePrefix
    ) {
        public SinkSection {
            type = blankTo(type, "KAFKA").toUpperCase(java.util.Locale.ROOT);
            outboxTable = blankTo(outboxTable, "osf_outbox");
            batchSize = batchSize <= 0 ? 100 : batchSize;
            lingerMs = lingerMs < 0 ? 20 : lingerMs;
            writerThreads = writerThreads <= 0 ? 2 : writerThreads;
            backpressure = blankTo(backpressure, "BLOCK").toUpperCase(java.util.Locale.ROOT);
            tablePrefix = tablePrefix == null ? "osf_" : tablePrefix;
        }

        public static SinkSection defaults() {
            return new SinkSection(null, null, null, null, null, 0, 20, 0, null, true, null);
        }

        public boolean usesDatabase() {
            return !"KAFKA".equals(type);
        }

        public boolean usesKafka() {
            return !"DB_OUTBOX".equals(type);
        }
    }

    /** Valores por defecto al crear topics que no existan. */
    public record TopicSection(int partitions, short replicationFactor, boolean autoCreate) {
        public TopicSection {
            partitions = partitions <= 0 ? 3 : partitions;
            replicationFactor = replicationFactor <= 0 ? 1 : replicationFactor;
        }

        public static TopicSection defaults() {
            return new TopicSection(3, (short) 1, true);
        }
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Vista en mapa para volcarla a YAML. */
    public Map<String, Object> toMap() {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> kafkaMap = new LinkedHashMap<>();
        kafkaMap.put("clusterType", kafka.clusterType().name());
        kafkaMap.put("bootstrapServers", kafka.bootstrapServers());
        kafkaMap.put("securityProtocol", kafka.securityProtocol());
        putIfPresent(kafkaMap, "saslMechanism", kafka.saslMechanism());
        putIfPresent(kafkaMap, "saslJaasConfig", kafka.saslJaasConfig());
        kafkaMap.put("clientId", kafka.clientId());
        kafkaMap.put("acks", kafka.acks());
        if (!kafka.extra().isEmpty()) {
            kafkaMap.put("extra", new LinkedHashMap<>(kafka.extra()));
        }
        root.put("kafka", kafkaMap);

        List<Map<String, Object>> domainList = new java.util.ArrayList<>();
        for (DomainMapping domain : domains) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("domain", domain.domain());
            entry.put("topic", domain.topic());
            putIfPresent(entry, "errorTopic", domain.errorTopic());
            entry.put("eventsPerSecond", domain.eventsPerSecond());
            entry.put("errorRate", domain.errorRate());
            entry.put("keyStrategy", domain.keyStrategy().name());
            putIfPresent(entry, "keyField", domain.keyField());
            domainList.add(entry);
        }
        root.put("domains", domainList);

        Map<String, Object> generationMap = new LinkedHashMap<>();
        generationMap.put("publishingMode", generation.publishingMode().name());
        generationMap.put("durationSeconds", generation.durationSeconds());
        generationMap.put("burstSize", generation.burstSize());
        generationMap.put("rampTargetEPS", generation.rampTargetEPS());
        root.put("generation", generationMap);

        Map<String, Object> serializationMap = new LinkedHashMap<>();
        serializationMap.put("format", serialization.format());
        serializationMap.put("prettyPrint", serialization.prettyPrint());
        putIfPresent(serializationMap, "schemaRegistryUrl", serialization.schemaRegistryUrl());
        root.put("serialization", serializationMap);

        Map<String, Object> sinkMap = new LinkedHashMap<>();
        sinkMap.put("type", sink.type());
        if (sink.usesDatabase()) {
            putIfPresent(sinkMap, "jdbcUrl", sink.jdbcUrl());
            putIfPresent(sinkMap, "username", sink.username());
            putIfPresent(sinkMap, "password", sink.password());
            sinkMap.put("outboxTable", sink.outboxTable());
            sinkMap.put("batchSize", sink.batchSize());
            sinkMap.put("lingerMs", sink.lingerMs());
            sinkMap.put("writerThreads", sink.writerThreads());
            sinkMap.put("backpressure", sink.backpressure());
            sinkMap.put("writeBusinessTable", sink.writeBusinessTable());
            sinkMap.put("tablePrefix", sink.tablePrefix());
        }
        root.put("sink", sinkMap);

        Map<String, Object> topicMap = new LinkedHashMap<>();
        topicMap.put("partitions", topic.partitions());
        topicMap.put("replicationFactor", (int) topic.replicationFactor());
        topicMap.put("autoCreate", topic.autoCreate());
        root.put("topic", topicMap);

        return root;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }
}
