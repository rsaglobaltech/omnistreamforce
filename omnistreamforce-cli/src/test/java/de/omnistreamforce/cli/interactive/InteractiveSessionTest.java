package de.omnistreamforce.cli.interactive;

import de.omnistreamforce.cli.console.ConsoleRenderer;
import de.omnistreamforce.cli.console.Prompter;
import de.omnistreamforce.domain.DomainRegistry;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.engine.PublishingMode;
import de.omnistreamforce.kafka.ClusterType;
import de.omnistreamforce.persistence.SinkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La conversacion completa, con un guion de respuestas y un cluster de mentira.
 * Los dominios son los reales descubiertos por ServiceLoader en el classpath del CLI.
 */
class InteractiveSessionTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private FakeClusterGateway gateway;
    private DomainRegistry registry;

    @BeforeEach
    void setUp() {
        gateway = new FakeClusterGateway(Map.of("fastfood-events", 3));
        registry = new DomainRegistry();
    }

    private SessionPlan runWith(String... answers) {
        String script = String.join(System.lineSeparator(), answers) + System.lineSeparator();
        PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
        Prompter prompter = new Prompter(new BufferedReader(new StringReader(script)), out);
        ConsoleRenderer console = new ConsoleRenderer(out, false);
        return new InteractiveSession(prompter, console, registry, config -> gateway).run();
    }

    private int domainIndexOf(String domain) {
        return new java.util.ArrayList<>(registry.listDomainNames()).indexOf(domain) + 1;
    }

    /** Guion completo del camino feliz: Kafka local, un dominio, destino Kafka. */
    private String[] happyPath() {
        return new String[]{
                "1",                       // tipo de cluster: local
                "localhost:9092",          // bootstrap
                "PLAINTEXT",               // protocolo
                String.valueOf(domainIndexOf("fastfood")),  // dominios
                "fastfood-events",         // topic (ya existe: no pregunta crearlo)
                "s",                       // topic de errores aparte
                "fastfood-errors",         // topic de errores
                "s",                       // crearlo (no existe)
                "3",                       // particiones
                "1",                       // replicacion
                "120",                     // eps
                "15",                      // tasa de error
                "1",                       // destino: Kafka
                "1",                       // formato: JSON
                "1",                       // modo: STEADY
                "0",                       // duracion ilimitada
                "2",                       // clave: ENTITY_ID
                "orderId",                 // campo clave
                "s"                        // confirmar
        };
    }

    @Test
    void happyPathCollectsEverythingTheEngineNeeds() {
        SessionPlan plan = runWith(happyPath());

        assertThat(gateway.connected).isTrue();
        assertThat(plan.kafka().bootstrapServers()).isEqualTo("localhost:9092");
        assertThat(plan.kafka().clusterType()).isEqualTo(ClusterType.LOCAL);
        assertThat(plan.sinkType()).isEqualTo(SinkType.KAFKA);
        assertThat(plan.persistence()).isNull();
        assertThat(plan.serializerFormat()).isEqualTo("JSON");
        assertThat(plan.publishingMode()).isEqualTo(PublishingMode.STEADY);
        assertThat(plan.durationSeconds()).isZero();
        assertThat(plan.keyStrategy()).isEqualTo(KeyStrategy.ENTITY_ID);
        assertThat(plan.keyField()).isEqualTo("orderId");

        assertThat(plan.domains()).hasSize(1);
        SessionPlan.DomainPlan domain = plan.domains().get(0);
        assertThat(domain.domain()).isEqualTo("fastfood");
        assertThat(domain.topic()).isEqualTo("fastfood-events");
        assertThat(domain.errorTopic()).isEqualTo("fastfood-errors");
        assertThat(domain.hasSeparateErrorTopic()).isTrue();
        assertThat(domain.eventsPerSecond()).isEqualTo(120);
        assertThat(domain.errorRate()).isEqualTo(15.0);
    }

    @Test
    void missingTopicsAreCreatedAndExistingOnesAreNot() {
        runWith(happyPath());

        // fastfood-events ya existia; solo se crea el de errores
        assertThat(gateway.createdTopics).containsExactly("fastfood-errors");
    }

    @Test
    void theSummaryShowsTheMappingBeforeConfirming() {
        runWith(happyPath());
        String printed = output.toString(StandardCharsets.UTF_8);

        assertThat(printed).contains("Resumen");
        assertThat(printed).contains("fastfood").contains("fastfood-events").contains("fastfood-errors");
        assertThat(printed).contains("120");
        assertThat(printed).contains("Total: 120 evt/s combinados");
    }

    @Test
    void decliningTheConfirmationAborts() {
        String[] answers = happyPath();
        answers[answers.length - 1] = "n";

        assertThatThrownBy(() -> runWith(answers))
                .isInstanceOf(Prompter.AbortedException.class);
    }

    @Test
    void databaseSinkAsksForTheConnection() {
        String[] answers = {
                "1", "localhost:9092", "PLAINTEXT",
                String.valueOf(domainIndexOf("fastfood")),
                "fastfood-events", "n",          // errores en el mismo topic
                "80", "5",
                "2",                              // destino: base de datos
                "jdbc:postgresql://localhost:5432/osf", "osf", "osf", "osf_outbox",
                "1", "1", "60", "1",             // JSON, STEADY, 60s, RANDOM
                "s"
        };

        SessionPlan plan = runWith(answers);

        assertThat(plan.sinkType()).isEqualTo(SinkType.DB_OUTBOX);
        assertThat(plan.persistence()).isNotNull();
        assertThat(plan.persistence().jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/osf");
        assertThat(plan.persistence().outboxTable()).isEqualTo("osf_outbox");
        assertThat(plan.durationSeconds()).isEqualTo(60);
        assertThat(plan.keyStrategy()).isEqualTo(KeyStrategy.RANDOM);
        assertThat(plan.keyField()).isNull();
        assertThat(plan.domains().get(0).hasSeparateErrorTopic()).isFalse();
    }

    @Test
    void dualSinkIsAvailableToo() {
        String[] answers = {
                "1", "localhost:9092", "PLAINTEXT",
                String.valueOf(domainIndexOf("fastfood")),
                "fastfood-events", "n", "50", "10",
                "3",                              // destino: dual
                "jdbc:postgresql://localhost:5432/osf", "osf", "osf", "osf_outbox",
                "1", "1", "0", "1", "s"
        };

        assertThat(runWith(answers).sinkType()).isEqualTo(SinkType.DUAL);
    }

    @Test
    void severalDomainsCanBePublishedAtOnce() {
        String domains = domainIndexOf("fastfood") + "," + domainIndexOf("ecommerce");
        String[] answers = {
                "1", "localhost:9092", "PLAINTEXT",
                domains,
                // fastfood: topic existente, errores en el mismo topic
                "fastfood-events", "n", "100", "10",
                // ecommerce: hay que crear los dos topics
                "ecommerce-orders", "s", "3", "1",
                "s", "ecommerce-errors", "s", "3", "1",
                "40", "20",
                // destino Kafka, JSON, STEADY, sin limite, clave RANDOM, confirmar
                "1", "1", "1", "0", "1", "s"
        };

        SessionPlan plan = runWith(answers);

        assertThat(plan.domains()).extracting(SessionPlan.DomainPlan::domain)
                .containsExactly("fastfood", "ecommerce");
        assertThat(plan.totalEventsPerSecond()).isEqualTo(140);
        assertThat(gateway.createdTopics).contains("ecommerce-orders", "ecommerce-errors");
    }

    @Test
    void aFailedConnectionSurfacesInsteadOfContinuing() {
        gateway.connectFailure = new IllegalStateException("broker inalcanzable");

        assertThatThrownBy(() -> runWith("1", "localhost:9092", "PLAINTEXT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broker inalcanzable");
    }
}
