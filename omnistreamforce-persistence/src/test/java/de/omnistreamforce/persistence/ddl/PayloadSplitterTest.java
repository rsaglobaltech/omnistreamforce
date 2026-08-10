package de.omnistreamforce.persistence.ddl;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.fastfood.FastFoodGenerator;
import de.omnistreamforce.persistence.dialect.PostgresDialect;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los generadores anaden al payload claves que el EventSchema no declara. Esas claves no se
 * pierden: acaban en payload_extra y por eso la deriva de esquema es medible.
 */
class PayloadSplitterTest {

    private final FastFoodGenerator generator = new FastFoodGenerator();
    private final TableModel table = new DdlGenerator(new PostgresDialect(), DdlOptions.defaults())
            .tableFor(generator.getSchema());

    @Test
    void undeclaredKeysOfARealEventAreIsolated() {
        Event event = generator.generateEvent(FastFoodGenerator.ORDER_PAID);
        Map<String, Object> extra = PayloadSplitter.undeclared(table, event.payload());

        assertThat(extra).containsKey("paidAt");        // el generador lo anade, el esquema no lo declara
        assertThat(extra).doesNotContainKeys("orderId", "brand", "totalAmount");
    }

    @Test
    void kitchenEventsIsolateTheirOwnExtraKeys() {
        Event event = generator.generateEvent(FastFoodGenerator.KITCHEN_PREP_STARTED);
        assertThat(PayloadSplitter.undeclared(table, event.payload())).containsKey("stationId");
    }

    @Test
    void errorEventsIsolateTheirDiagnosticKeys() {
        Event event = generator.generateEvent(FastFoodGenerator.ERR_COLD_CHAIN_BREACH);
        Map<String, Object> extra = PayloadSplitter.undeclared(table, event.payload());

        assertThat(extra).containsKeys("freezerId", "temperatureCelsius", "thresholdCelsius", "breachMinutes");
        assertThat(extra).doesNotContainKey("ingredientSku");
    }

    @Test
    void anEventWithOnlyDeclaredFieldsHasNoExtras() {
        Event event = generator.generateEvent(FastFoodGenerator.ORDER_PLACED);
        assertThat(PayloadSplitter.undeclared(table, event.payload())).isEmpty();
    }

    @Test
    void emptyOrNullPayloadIsHandled() {
        assertThat(PayloadSplitter.undeclared(table, null)).isEmpty();
        assertThat(PayloadSplitter.undeclared(table, Map.of())).isEmpty();
    }
}
