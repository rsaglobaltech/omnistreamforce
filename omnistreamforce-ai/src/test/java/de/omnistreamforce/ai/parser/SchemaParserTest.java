package de.omnistreamforce.ai.parser;

import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.EventType;
import de.omnistreamforce.core.FieldDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo que devuelve un modelo rara vez es JSON limpio: aqui se fija cuanta suciedad se tolera.
 */
class SchemaParserTest {

    private final SchemaParser parser = new SchemaParser();

    private static final String CLEAN = """
            {
              "domain": "logistics",
              "name": "LogisticsEvents",
              "description": "Eventos de logistica",
              "fields": [
                {"name":"shipmentId","type":"string","required":true,"description":"Envio"},
                {"name":"weightKg","type":"double","required":false},
                {"name":"status","type":"string","enumValues":["NEW","IN_TRANSIT","DELIVERED"]}
              ],
              "normalEvents": [
                {"name":"ShipmentCreated","description":"alta"},
                {"name":"ShipmentDelivered","description":"entrega"}
              ],
              "errorEvents": [{"name":"ShipmentLost","description":"perdida"}],
              "errorRate": 0.2
            }""";

    @Test
    void parsesACleanResponse() {
        EventSchema schema = parser.parse(CLEAN, "fallback");

        assertThat(schema.domain()).isEqualTo("logistics");
        assertThat(schema.name()).isEqualTo("LogisticsEvents");
        assertThat(schema.fields()).hasSize(3);
        assertThat(schema.eventTypes()).hasSize(3);
        assertThat(schema.eventTypes().stream()
                .filter(spec -> spec.eventType() == EventType.ERROR).count()).isEqualTo(1);
        assertThat(schema.errorSpec().errorRate()).isEqualTo(0.2);
        assertThat(schema.errorSpec().errorTypes()).containsExactly("ShipmentLost");
    }

    @Test
    void stripsMarkdownFences() {
        String response = "Aqui tienes el esquema:\n```json\n" + CLEAN + "\n```\nEspero que sirva.";

        assertThat(parser.parse(response, "fallback").domain()).isEqualTo("logistics");
    }

    @Test
    void toleratesTextBeforeAndAfterTheJson() {
        String response = "Claro. " + CLEAN + " Dime si quieres mas campos.";

        assertThat(parser.parse(response, "fallback").fields()).hasSize(3);
    }

    @Test
    void aFieldWithEnumValuesBecomesAnEnumEvenIfTheModelSaysString() {
        EventSchema schema = parser.parse(CLEAN, "fallback");

        FieldDefinition status = schema.fields().stream()
                .filter(field -> field.name().equals("status")).findFirst().orElseThrow();
        assertThat(status.type()).isEqualTo("enum");
        assertThat(status.enumValues()).containsExactly("NEW", "IN_TRANSIT", "DELIVERED");
    }

    @Test
    void unusualTypeNamesAreNormalized() {
        assertThat(parser.normalizeType("Integer", false)).isEqualTo("int");
        assertThat(parser.normalizeType("LONG", false)).isEqualTo("int");
        assertThat(parser.normalizeType("float", false)).isEqualTo("double");
        assertThat(parser.normalizeType("decimal", false)).isEqualTo("double");
        assertThat(parser.normalizeType("bool", false)).isEqualTo("boolean");
        assertThat(parser.normalizeType("list", false)).isEqualTo("array");
        assertThat(parser.normalizeType("struct", false)).isEqualTo("object");
        assertThat(parser.normalizeType("timestamp", false)).isEqualTo("datetime");
        // un tipo que nadie entiende no rompe el esquema: se guarda como texto
        assertThat(parser.normalizeType("quaternion", false)).isEqualTo("string");
    }

    @Test
    void eventsGivenAsPlainStringsAlsoWork() {
        String response = """
                {"domain":"iot","fields":[{"name":"deviceId","type":"string"}],
                 "normalEvents":["Heartbeat","Reading"],"errorEvents":["SensorFault"]}""";

        EventSchema schema = parser.parse(response, "iot");

        assertThat(schema.eventTypes()).extracting(spec -> spec.typeName())
                .containsExactly("Heartbeat", "Reading", "SensorFault");
    }

    @Test
    void alternativeKeyNamesAreAccepted() {
        String response = """
                {"domain":"iot","fields":[{"name":"deviceId","type":"string"}],
                 "events":["Heartbeat"],"errors":["SensorFault"]}""";

        assertThat(parser.parse(response, "iot").eventTypes()).hasSize(2);
    }

    @Test
    void missingDomainFallsBackToTheRequestedOne() {
        String response = """
                {"fields":[{"name":"a","type":"string"}],"normalEvents":["X"]}""";

        assertThat(parser.parse(response, "pedido").domain()).isEqualTo("pedido");
        assertThat(parser.parse(response, "pedido").name()).isEqualTo("PedidoEvents");
    }

    @Test
    void duplicatedFieldsAndEventsAreCollapsed() {
        String response = """
                {"domain":"x","fields":[{"name":"a","type":"string"},{"name":"a","type":"int"}],
                 "normalEvents":["E","E"],"errorEvents":["F"]}""";

        EventSchema schema = parser.parse(response, "x");

        assertThat(schema.fields()).hasSize(1);
        assertThat(schema.eventTypes()).hasSize(2);
    }

    @Test
    void anErrorRateGivenAsPercentageIsConverted() {
        String response = """
                {"domain":"x","fields":[{"name":"a","type":"string"}],
                 "normalEvents":["E"],"errorRate":25}""";

        assertThat(parser.parse(response, "x").errorSpec().errorRate()).isEqualTo(0.25);
    }

    @Test
    void fieldsWithoutNameAreIgnored() {
        String response = """
                {"domain":"x","fields":[{"type":"string"},{"name":"ok","type":"string"}],
                 "normalEvents":["E"]}""";

        assertThat(parser.parse(response, "x").fields()).hasSize(1);
    }

    @Test
    void uselessResponsesAreRejectedWithAClearMessage() {
        assertThatThrownBy(() -> parser.parse("", "x"))
                .isInstanceOf(SchemaParser.ParseException.class)
                .hasMessageContaining("no devolvio nada");

        assertThatThrownBy(() -> parser.parse("lo siento, no puedo ayudarte", "x"))
                .isInstanceOf(SchemaParser.ParseException.class)
                .hasMessageContaining("No se encontro un objeto JSON");

        assertThatThrownBy(() -> parser.parse("{esto no es json}", "x"))
                .isInstanceOf(SchemaParser.ParseException.class)
                .hasMessageContaining("no es JSON valido");

        assertThatThrownBy(() -> parser.parse("{\"domain\":\"x\",\"fields\":[]}", "x"))
                .isInstanceOf(SchemaParser.ParseException.class)
                .hasMessageContaining("ningun campo");

        assertThatThrownBy(() -> parser.parse(
                "{\"domain\":\"x\",\"fields\":[{\"name\":\"a\",\"type\":\"string\"}]}", "x"))
                .isInstanceOf(SchemaParser.ParseException.class)
                .hasMessageContaining("eventos normales");
    }
}
