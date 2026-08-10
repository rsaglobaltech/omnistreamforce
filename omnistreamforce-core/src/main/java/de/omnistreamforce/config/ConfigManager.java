package de.omnistreamforce.config;

import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.engine.PublishingMode;
import de.omnistreamforce.kafka.ClusterType;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Carga, guarda, combina y valida la configuracion en YAML.
 * <p>
 * El mapeo se hace a mano en vez de dejarselo a SnakeYAML: asi el fichero admite claves
 * ausentes, enums escritos en minusculas y numeros como texto, y un YAML mal escrito produce un
 * mensaje concreto en vez de una excepcion de reflexion.
 */
public class ConfigManager {

    private final PlaceholderResolver resolver;

    public ConfigManager() {
        this(new PlaceholderResolver());
    }

    public ConfigManager(PlaceholderResolver resolver) {
        this.resolver = resolver;
    }

    public OmniStreamForceConfig loadDefault() {
        return OmniStreamForceConfig.defaults();
    }

    public OmniStreamForceConfig load(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la configuracion: " + file, e);
        }
    }

    public OmniStreamForceConfig load(InputStream input) {
        return load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    public OmniStreamForceConfig load(Reader reader) {
        Object raw = new Yaml().load(reader);
        if (raw == null) {
            return OmniStreamForceConfig.defaults();
        }
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException("La configuracion debe ser un mapa YAML");
        }
        Map<String, Object> root = (Map<String, Object>) resolver.resolve(raw);
        return fromMap(root);
    }

    public void save(OmniStreamForceConfig config, Path file) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                new Yaml(options).dump(config.toMap(), writer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la configuracion: " + file, e);
        }
    }

    /**
     * Combina dos configuraciones: gana {@code override} en lo que trae, y se conserva de
     * {@code base} lo que no. La lista de dominios se reemplaza entera si el override trae alguno.
     */
    public OmniStreamForceConfig merge(OmniStreamForceConfig base, OmniStreamForceConfig override) {
        if (base == null) {
            return override;
        }
        if (override == null) {
            return base;
        }
        OmniStreamForceConfig defaults = OmniStreamForceConfig.defaults();
        return new OmniStreamForceConfig(
                override.kafka().equals(defaults.kafka()) ? base.kafka() : override.kafka(),
                override.domains().isEmpty() ? base.domains() : override.domains(),
                override.generation().equals(defaults.generation()) ? base.generation() : override.generation(),
                override.serialization().equals(defaults.serialization())
                        ? base.serialization() : override.serialization(),
                override.sink().equals(defaults.sink()) ? base.sink() : override.sink(),
                override.topic().equals(defaults.topic()) ? base.topic() : override.topic());
    }

    /** Errores de configuracion; lista vacia si todo esta bien. */
    public List<String> validate(OmniStreamForceConfig config) {
        List<String> errors = new ArrayList<>();
        if (config.domains().isEmpty()) {
            errors.add("No hay dominios configurados: anade al menos uno en 'domains'");
        }
        List<String> topics = new ArrayList<>();
        for (OmniStreamForceConfig.DomainMapping domain : config.domains()) {
            if (domain.domain() == null || domain.domain().isBlank()) {
                errors.add("Hay un dominio sin nombre en 'domains'");
            }
            if (domain.topic() == null || domain.topic().isBlank()) {
                errors.add("El dominio '" + domain.domain() + "' no tiene topic de destino");
            }
            if (domain.keyStrategy() == KeyStrategy.ENTITY_ID
                    && (domain.keyField() == null || domain.keyField().isBlank())) {
                errors.add("El dominio '" + domain.domain()
                        + "' usa ENTITY_ID pero no indica 'keyField'");
            }
            if (topics.contains(domain.domain())) {
                errors.add("El dominio '" + domain.domain() + "' esta configurado dos veces");
            }
            topics.add(domain.domain());
        }

        OmniStreamForceConfig.SinkSection sink = config.sink();
        if (!List.of("KAFKA", "DB_OUTBOX", "DUAL").contains(sink.type())) {
            errors.add("Destino desconocido: '" + sink.type() + "' (usa KAFKA, DB_OUTBOX o DUAL)");
        }
        if (sink.usesDatabase() && (sink.jdbcUrl() == null || sink.jdbcUrl().isBlank())) {
            errors.add("El destino " + sink.type() + " requiere 'sink.jdbcUrl'");
        }
        if (sink.usesKafka() && (config.kafka().bootstrapServers() == null
                || config.kafka().bootstrapServers().isBlank())) {
            errors.add("El destino " + sink.type() + " requiere 'kafka.bootstrapServers'");
        }
        if (!List.of("JSON", "AVRO", "PROTOBUF", "PROTO")
                .contains(config.serialization().format().toUpperCase(Locale.ROOT))) {
            errors.add("Formato de serializacion desconocido: " + config.serialization().format());
        }
        if (config.serialization().format().toUpperCase(Locale.ROOT).startsWith("PROTO")
                && sink.usesDatabase()) {
            // el outbox guarda el evento como JSON en una columna jsonb
            errors.add("El destino " + sink.type() + " requiere formato JSON");
        }
        return errors;
    }

    // --- mapeo desde YAML ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private OmniStreamForceConfig fromMap(Map<String, Object> root) {
        Map<String, Object> kafka = section(root, "kafka");
        Map<String, Object> generation = section(root, "generation");
        Map<String, Object> serialization = section(root, "serialization");
        Map<String, Object> sink = section(root, "sink");
        Map<String, Object> topic = section(root, "topic");

        OmniStreamForceConfig.KafkaSection kafkaSection = new OmniStreamForceConfig.KafkaSection(
                enumValue(kafka.get("clusterType"), ClusterType.class, ClusterType.LOCAL),
                string(kafka.get("bootstrapServers")),
                string(kafka.get("securityProtocol")),
                string(kafka.get("saslMechanism")),
                string(kafka.get("saslJaasConfig")),
                string(kafka.get("clientId")),
                string(kafka.get("acks")),
                stringMap(kafka.get("extra")));

        List<OmniStreamForceConfig.DomainMapping> domains = new ArrayList<>();
        Object rawDomains = root.get("domains");
        if (rawDomains instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map)) {
                    throw new IllegalArgumentException("Cada entrada de 'domains' debe ser un mapa");
                }
                Map<String, Object> entry = (Map<String, Object>) item;
                domains.add(new OmniStreamForceConfig.DomainMapping(
                        string(entry.get("domain")),
                        string(entry.get("topic")),
                        string(entry.get("errorTopic")),
                        integer(entry.get("eventsPerSecond"), 50),
                        decimal(entry.get("errorRate"), 0),
                        enumValue(entry.get("keyStrategy"), KeyStrategy.class, KeyStrategy.RANDOM),
                        string(entry.get("keyField"))));
            }
        }

        return new OmniStreamForceConfig(
                kafkaSection,
                domains,
                new OmniStreamForceConfig.GenerationSection(
                        enumValue(generation.get("publishingMode"), PublishingMode.class, PublishingMode.STEADY),
                        integer(generation.get("durationSeconds"), 0),
                        integer(generation.get("burstSize"), 0),
                        integer(generation.get("rampTargetEPS"), 0)),
                new OmniStreamForceConfig.SerializationSection(
                        string(serialization.get("format")),
                        bool(serialization.get("prettyPrint"), false),
                        string(serialization.get("schemaRegistryUrl"))),
                new OmniStreamForceConfig.SinkSection(
                        string(sink.get("type")),
                        string(sink.get("jdbcUrl")),
                        string(sink.get("username")),
                        string(sink.get("password")),
                        string(sink.get("outboxTable")),
                        integer(sink.get("batchSize"), 100),
                        integer(sink.get("lingerMs"), 20),
                        integer(sink.get("writerThreads"), 2),
                        string(sink.get("backpressure")),
                        bool(sink.get("writeBusinessTable"), true),
                        string(sink.get("tablePrefix"))),
                new OmniStreamForceConfig.TopicSection(
                        integer(topic.get("partitions"), 3),
                        (short) integer(topic.get("replicationFactor"), 1),
                        bool(topic.get("autoCreate"), true)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> section(Map<String, Object> root, String name) {
        Object value = root.get(name);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("La seccion '" + name + "' debe ser un mapa");
        }
        return (Map<String, Object>) value;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int integer(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("No es un numero entero: " + value);
        }
    }

    private double decimal(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("No es un numero: " + value);
        }
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private <E extends Enum<E>> E enumValue(Object value, Class<E> type, E fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        String name = String.valueOf(value).trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Valor no valido para " + type.getSimpleName()
                    + ": '" + value + "'. Admitidos: " + List.of(type.getEnumConstants()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        ((Map<Object, Object>) value).forEach((key, item) ->
                result.put(String.valueOf(key), String.valueOf(item)));
        return result;
    }
}
