package de.omnistreamforce.ai;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.domain.DomainGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchemaProposerTest {

    private static final String SCHEMA_JSON = """
            {"domain":"logistics","name":"LogisticsEvents","description":"paqueteria",
             "fields":[
               {"name":"shipmentId","type":"string","required":true},
               {"name":"weightKg","type":"double"},
               {"name":"status","type":"string","enumValues":["NEW","DELIVERED"]},
               {"name":"customerEmail","type":"string"},
               {"name":"items","type":"array"}
             ],
             "normalEvents":["ShipmentCreated","ShipmentDelivered"],
             "errorEvents":["ShipmentLost"],
             "errorRate":0.15}""";

    private AIConfig config() {
        return AIConfig.builder(AIProvider.OLLAMA).maxRetries(0).retryBackoffMs(1).build();
    }

    private String ollamaReply(String content) {
        return "{\"message\":{\"content\":\""
                + content.replace("\"", "\\\"").replace("\n", " ") + "\"}}";
    }

    private SchemaProposer proposerReturning(String content) {
        FakeHttpTransport transport = new FakeHttpTransport().respondWith(200, ollamaReply(content));
        return new SchemaProposer(new OllamaService(config(), transport));
    }

    @Test
    void proposesAndCachesTheSchema() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .respondWith(200, ollamaReply(SCHEMA_JSON));
        SchemaProposer proposer = new SchemaProposer(new OllamaService(config(), transport));

        EventSchema first = proposer.propose("logistics", "paqueteria urbana");
        EventSchema second = proposer.propose("logistics", "paqueteria urbana");

        assertThat(first.domain()).isEqualTo("logistics");
        assertThat(second).isSameAs(first);
        // la segunda vez no se vuelve a preguntar al modelo
        assertThat(transport.calls).hasSize(1);
        assertThat(proposer.cached("logistics")).contains(first);
    }

    @Test
    void anUnusableSchemaIsRejected() {
        SchemaProposer proposer = proposerReturning(
                "{\"domain\":\"x\",\"fields\":[{\"name\":\"solo\",\"type\":\"string\"}],"
                        + "\"normalEvents\":[\"E\"]}");

        assertThatThrownBy(() -> proposer.propose("x", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 2 campos");
    }

    @Test
    void tryProposeSwallowsTheFailure() {
        SchemaProposer proposer = new SchemaProposer(
                new OllamaService(config(), new FakeHttpTransport().respondWith(500, "boom")));

        assertThat(proposer.tryPropose("x", null)).isEmpty();
    }

    @Test
    void theProposedSchemaBecomesAWorkingGenerator() {
        DomainGenerator generator = proposerReturning(SCHEMA_JSON)
                .proposeGenerator("logistics", "paqueteria urbana");

        assertThat(generator.getDomainName()).isEqualTo("logistics");
        assertThat(generator.getSupportedEventTypes())
                .containsExactly("ShipmentCreated", "ShipmentDelivered");
        assertThat(generator.getErrorTypes()).containsExactly("ShipmentLost");

        Event event = generator.generateEvent("ShipmentCreated");
        assertThat(event.eventType()).isEqualTo("NORMAL");
        assertThat(event.domain()).isEqualTo("logistics");
        assertThat(event.payload()).containsKeys("shipmentId", "weightKg", "status",
                "customerEmail", "items");
        // los valores siguen el tipo declarado
        assertThat(event.payload().get("weightKg")).isInstanceOf(Double.class);
        assertThat(event.payload().get("status")).isIn("NEW", "DELIVERED");
        assertThat((List<?>) event.payload().get("items")).isNotEmpty();
        // el nombre del campo guia el dato: un email parece un email
        assertThat((String) event.payload().get("customerEmail")).contains("@");
    }

    @Test
    void errorEventsDegradeThePayloadInAVisibleWay() {
        DomainGenerator generator = proposerReturning(SCHEMA_JSON)
                .proposeGenerator("logistics", null);

        for (int i = 0; i < 40; i++) {
            Event event = generator.generateErrorEvent();

            assertThat(event.eventType()).isEqualTo("ERROR");
            assertThat(event.metadata()).containsKeys("errorType", "errorMessage", "severity");
            assertThat(event.payload()).containsKey("corruptedField");

            String victim = (String) event.payload().get("corruptedField");
            Object value = event.payload().get(victim);
            boolean degraded = !event.payload().containsKey(victim)   // campo eliminado
                    || value == null
                    || "!!INVALID!!".equals(value)
                    || value instanceof Number number && number.longValue() > 1_000_000_000L
                    || value instanceof String text && text.length() > 500;
            assertThat(degraded)
                    .describedAs("el campo %s deberia estar degradado, valor=%s", victim, value)
                    .isTrue();
        }
    }

    @Test
    void aHandWrittenSchemaCanBeRegisteredWithoutAI() {
        SchemaProposer proposer = new SchemaProposer(AIServiceFactory.create(AIConfig.disabled()));
        EventSchema schema = new de.omnistreamforce.ai.parser.SchemaParser()
                .parse(SCHEMA_JSON, "logistics");

        proposer.register(schema);

        assertThat(proposer.cached("logistics")).isPresent();
        assertThat(proposer.propose("logistics", null)).isSameAs(schema);
    }

    @Test
    void registeringAnInvalidSchemaIsRejected() {
        SchemaProposer proposer = new SchemaProposer(AIServiceFactory.create(AIConfig.disabled()));
        EventSchema broken = new EventSchema("x", "X", "d", List.of(), List.of(),
                new de.omnistreamforce.core.ErrorSpec(List.of(), 0.1, List.of()));

        assertThatThrownBy(() -> proposer.register(broken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clearingTheCacheForcesANewProposal() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .respondWith(200, ollamaReply(SCHEMA_JSON));
        SchemaProposer proposer = new SchemaProposer(new OllamaService(config(), transport));

        proposer.propose("logistics", null);
        proposer.clearCache();
        proposer.propose("logistics", null);

        assertThat(transport.calls).hasSize(2);
    }

    @Test
    void theGeneratorRefusesEventTypesOutsideTheSchema() {
        DomainGenerator generator = proposerReturning(SCHEMA_JSON).proposeGenerator("logistics", null);

        assertThatThrownBy(() -> generator.generateEvent("NoExiste"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSchemaWithoutErrorsStillGeneratesNormalEvents() {
        DomainGenerator generator = proposerReturning("""
                {"domain":"iot","fields":[{"name":"deviceId","type":"string"},
                 {"name":"reading","type":"double"}],"normalEvents":["Heartbeat"]}""")
                .proposeGenerator("iot", null);

        assertThat(generator.getErrorTypes()).isEmpty();
        Map<String, Object> payload = generator.generateEvent("Heartbeat").payload();
        assertThat(payload).containsKeys("deviceId", "reading");
        assertThatThrownBy(generator::generateErrorEvent).isInstanceOf(IllegalStateException.class);
    }
}
