package de.omnistreamforce.cli.interactive;

import de.omnistreamforce.cli.cluster.ClusterGateway;
import de.omnistreamforce.cli.console.ConsoleRenderer;
import de.omnistreamforce.cli.console.Prompter;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.domain.DomainGenerator;
import de.omnistreamforce.domain.DomainRegistry;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.engine.PublishingMode;
import de.omnistreamforce.kafka.ClusterInfo;
import de.omnistreamforce.kafka.ClusterType;
import de.omnistreamforce.kafka.KafkaConnectionConfig;
import de.omnistreamforce.kafka.MskClusterConfig;
import de.omnistreamforce.persistence.PersistenceConfig;
import de.omnistreamforce.persistence.SinkType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Flujo guiado que recoge todo lo necesario para empezar a publicar.
 * <p>
 * No publica nada: devuelve un {@link SessionPlan}. Asi la conversacion se puede probar entera
 * con un guion de respuestas y un cluster de mentira.
 */
public class InteractiveSession {

    private static final List<String> FORMATS = List.of("JSON", "AVRO", "PROTOBUF");

    private final Prompter prompt;
    private final ConsoleRenderer console;
    private final DomainRegistry registry;
    private final Function<KafkaConnectionConfig, ClusterGateway> gatewayFactory;

    private ClusterGateway gateway;

    public InteractiveSession(Prompter prompt,
                              ConsoleRenderer console,
                              DomainRegistry registry,
                              Function<KafkaConnectionConfig, ClusterGateway> gatewayFactory) {
        this.prompt = prompt;
        this.console = console;
        this.registry = registry;
        this.gatewayFactory = gatewayFactory;
    }

    /** El gateway conectado durante la sesion, para reutilizarlo al publicar. */
    public ClusterGateway gateway() {
        return gateway;
    }

    public SessionPlan run() {
        KafkaConnectionConfig kafka = askConnection();
        Map<String, Integer> topics = askTopics();
        List<SessionPlan.DomainPlan> domains = askDomains(topics);
        SinkType sinkType = askSink();
        PersistenceConfig persistence = sinkType == SinkType.KAFKA ? null : askPersistence();
        SessionPlan plan = askGlobalConfig(kafka, sinkType, persistence, domains);
        showSummary(plan);
        if (!prompt.confirm("Empezar a publicar?", true)) {
            throw new Prompter.AbortedException("Cancelado por el usuario");
        }
        return plan;
    }

    // --- Paso 1: conexion ----------------------------------------------------

    private KafkaConnectionConfig askConnection() {
        console.step(1, "Conexion a Kafka");
        int choice = prompt.choose("Tipo de cluster",
                List.of("Local / Self-hosted", "AWS MSK", "Confluent Cloud"), 0);

        KafkaConnectionConfig config = switch (choice) {
            case 1 -> askMsk();
            case 2 -> askConfluent();
            default -> askLocal();
        };

        gateway = gatewayFactory.apply(config);
        ClusterInfo info = gateway.connect();
        console.success("Conectado a " + info.clusterId()
                + " (" + info.brokers().size() + " broker(s), " + info.kafkaVersion() + ")");
        return config;
    }

    private KafkaConnectionConfig askLocal() {
        String bootstrap = prompt.ask("Bootstrap servers", "localhost:9092");
        String protocol = prompt.ask("Security protocol", "PLAINTEXT");
        return KafkaConnectionConfig.builder()
                .clusterType(ClusterType.LOCAL)
                .bootstrapServers(bootstrap)
                .securityProtocol(protocol)
                .clientId("omnistreamforce-cli")
                .acks("all")
                .build();
    }

    private KafkaConnectionConfig askMsk() {
        String bootstrap = prompt.askRequired("Bootstrap servers de MSK");
        int auth = prompt.choose("Autenticacion", List.of("IAM", "SASL/SCRAM"), 0);
        String region = prompt.ask("Region de AWS", "us-east-1");

        MskClusterConfig msk = auth == 0
                ? new MskClusterConfig(bootstrap, MskClusterConfig.AuthType.IAM, region, null, null, null)
                : new MskClusterConfig(bootstrap, MskClusterConfig.AuthType.SCRAM, region, null,
                prompt.askRequired("Usuario SCRAM"), prompt.askRequired("Password SCRAM"));

        KafkaConnectionConfig.Builder builder = KafkaConnectionConfig.builder()
                .clusterType(ClusterType.MSK)
                .bootstrapServers(bootstrap)
                .clientId("omnistreamforce-cli")
                .acks("all");
        msk.toKafkaProps().forEach((key, value) -> builder.extraProp(String.valueOf(key), String.valueOf(value)));
        return builder.build();
    }

    private KafkaConnectionConfig askConfluent() {
        String bootstrap = prompt.askRequired("Bootstrap servers de Confluent Cloud");
        String apiKey = prompt.askRequired("API key");
        String apiSecret = prompt.askRequired("API secret");
        return KafkaConnectionConfig.builder()
                .clusterType(ClusterType.CONFLUENT)
                .bootstrapServers(bootstrap)
                .securityProtocol("SASL_SSL")
                .saslMechanism("PLAIN")
                .saslJaasConfig("org.apache.kafka.common.security.plain.PlainLoginModule required"
                        + " username=\"" + apiKey + "\" password=\"" + apiSecret + "\";")
                .clientId("omnistreamforce-cli")
                .acks("all")
                .build();
    }

    // --- Paso 2: topics ------------------------------------------------------

    private Map<String, Integer> askTopics() {
        console.step(2, "Topics del cluster");
        Map<String, Integer> topics = new LinkedHashMap<>(gateway.listTopicsWithPartitions());
        if (topics.isEmpty()) {
            console.warn("El cluster no tiene topics; se crearan al vuelo segun el mapeo de dominios.");
            return topics;
        }
        List<List<String>> rows = new ArrayList<>();
        topics.forEach((name, partitions) -> rows.add(List.of(name, String.valueOf(partitions))));
        console.table(List.of("Topic", "Particiones"), rows);
        return topics;
    }

    // --- Paso 3: dominios y mapeo -------------------------------------------

    private List<SessionPlan.DomainPlan> askDomains(Map<String, Integer> existingTopics) {
        console.step(3, "Dominios y mapeo a topics");
        List<String> available = new ArrayList<>(registry.listDomainNames());
        if (available.isEmpty()) {
            throw new IllegalStateException("No hay dominios registrados en el classpath");
        }

        List<Integer> chosen = prompt.chooseMany("Dominios a publicar", available, List.of(0));
        List<SessionPlan.DomainPlan> plans = new ArrayList<>();

        for (int index : chosen) {
            String domain = available.get(index);
            console.println();
            console.info("Dominio: " + domain);
            describeDomain(domain);

            // cada topic se crea justo despues de elegirlo, mientras el usuario lo tiene en mente
            String topic = prompt.ask("Topic de destino", domain + "-events");
            ensureTopic(topic, existingTopics);

            boolean separate = prompt.confirm("Publicar los errores en un topic aparte?", true);
            String errorTopic = topic;
            if (separate) {
                errorTopic = prompt.ask("Topic de errores", topic + "-errors");
                ensureTopic(errorTopic, existingTopics);
            }

            int eps = prompt.askInt("Eventos por segundo", 50, 1, 100_000);
            double errorRate = prompt.askDouble("Tasa de error (%)", 10.0, 0.0, 100.0);
            plans.add(new SessionPlan.DomainPlan(domain, topic, errorTopic, eps, errorRate));
        }
        return plans;
    }

    private void describeDomain(String domain) {
        registry.get(domain).map(DomainGenerator::getSchema).ifPresent(schema -> {
            console.info("  " + schema.description());
            console.info("  Eventos normales: " + String.join(", ", normalTypes(schema, domain)));
            console.info("  Eventos de error: " + String.join(", ",
                    registry.get(domain).map(DomainGenerator::getErrorTypes).orElse(List.of())));
        });
    }

    private List<String> normalTypes(EventSchema schema, String domain) {
        return registry.get(domain).map(DomainGenerator::getSupportedEventTypes).orElse(List.of());
    }

    private void ensureTopic(String topic, Map<String, Integer> existingTopics) {
        if (existingTopics.containsKey(topic)) {
            return;
        }
        if (prompt.confirm("El topic '" + topic + "' no existe. Crearlo?", true)) {
            int partitions = prompt.askInt("Particiones", 3, 1, 1000);
            int replication = prompt.askInt("Factor de replicacion", 1, 1, 10);
            gateway.verifyOrCreateTopic(topic, partitions, (short) replication);
            existingTopics.put(topic, partitions);
            console.success("Topic creado: " + topic);
        }
    }

    // --- Paso 4: destino -----------------------------------------------------

    private SinkType askSink() {
        console.step(4, "Destino de los eventos");
        int choice = prompt.choose("Donde se escriben los eventos", List.of(
                "Kafka (directo)",
                "Base de datos con patron Outbox",
                "Ambos (dual: Kafka + base de datos)"), 0);
        return switch (choice) {
            case 1 -> SinkType.DB_OUTBOX;
            case 2 -> SinkType.DUAL;
            default -> SinkType.KAFKA;
        };
    }

    private PersistenceConfig askPersistence() {
        String url = prompt.ask("URL JDBC", "jdbc:postgresql://localhost:5432/osf");
        String user = prompt.ask("Usuario", "osf");
        String password = prompt.ask("Password", "osf");
        String outbox = prompt.ask("Tabla outbox", PersistenceConfig.DEFAULT_OUTBOX_TABLE);
        return PersistenceConfig.builder(url)
                .username(user)
                .password(password)
                .outboxTable(outbox)
                .build();
    }

    // --- Paso 5: configuracion global ---------------------------------------

    private SessionPlan askGlobalConfig(KafkaConnectionConfig kafka, SinkType sinkType,
                                        PersistenceConfig persistence,
                                        List<SessionPlan.DomainPlan> domains) {
        console.step(5, "Configuracion de publicacion");
        String format = FORMATS.get(prompt.choose("Formato de evento", FORMATS, 0));

        List<String> modes = List.of("STEADY", "BURST", "SPIKE", "RAMP");
        PublishingMode mode = PublishingMode.valueOf(modes.get(prompt.choose("Modo de publicacion", modes, 0)));

        long duration = prompt.askInt("Duracion en segundos (0 = ilimitado)", 0, 0, 86_400);

        List<String> strategies = List.of("RANDOM", "ENTITY_ID", "ROUND_ROBIN");
        KeyStrategy keyStrategy = KeyStrategy.valueOf(
                strategies.get(prompt.choose("Estrategia de clave", strategies, 0)));
        String keyField = keyStrategy == KeyStrategy.ENTITY_ID
                ? prompt.ask("Campo del payload usado como clave", "orderId")
                : null;

        return new SessionPlan(kafka, sinkType, persistence, domains, format, mode,
                duration, keyStrategy, keyField);
    }

    private void showSummary(SessionPlan plan) {
        console.title("Resumen");
        List<List<String>> rows = new ArrayList<>();
        for (SessionPlan.DomainPlan domain : plan.domains()) {
            rows.add(List.of(
                    domain.domain(),
                    domain.topic(),
                    domain.hasSeparateErrorTopic() ? domain.errorTopic() : "(mismo topic)",
                    String.valueOf(domain.eventsPerSecond()),
                    String.format("%.1f%%", domain.errorRate())));
        }
        console.table(List.of("Dominio", "Topic", "Topic de errores", "EPS", "Errores"), rows);
        console.info("Destino: " + plan.sinkType()
                + " | formato: " + plan.serializerFormat()
                + " | modo: " + plan.publishingMode()
                + " | duracion: " + (plan.durationSeconds() == 0 ? "ilimitada" : plan.durationSeconds() + "s")
                + " | clave: " + plan.keyStrategy()
                + (plan.keyField() == null ? "" : " (" + plan.keyField() + ")"));
        console.info("Total: " + plan.totalEventsPerSecond() + " evt/s combinados");
    }
}
