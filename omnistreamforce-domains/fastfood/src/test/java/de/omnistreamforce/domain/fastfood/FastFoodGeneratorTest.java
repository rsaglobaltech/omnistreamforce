package de.omnistreamforce.domain.fastfood;

import de.omnistreamforce.core.Event;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FastFoodGeneratorTest {

    private final FastFoodGenerator generator = new FastFoodGenerator();

    @Test
    void schemaIsComplete() {
        assertThat(generator.getDomainName()).isEqualTo("fastfood");
        assertThat(generator.getSupportedEventTypes()).hasSize(13);
        assertThat(generator.getErrorTypes()).hasSize(8);
        assertThat(generator.getSchema().eventTypes()).hasSize(21);
        assertThat(generator.getSchema().errorSpec().errorRate()).isEqualTo(0.12);
    }

    @Test
    void schemaExposesEveryBrandAsEnumValue() {
        List<String> brandValues = generator.getSchema().fields().stream()
                .filter(f -> f.name().equals("brand"))
                .findFirst()
                .orElseThrow()
                .enumValues();
        assertThat(brandValues).containsExactlyInAnyOrder("BURGER_KING", "MCDONALDS");
    }

    @Test
    void generatesEachNormalEventTypeWithBrandAndStore() {
        for (String type : generator.getSupportedEventTypes()) {
            Event event = generator.generateEvent(type);
            assertThat(event.eventType()).isEqualTo("NORMAL");
            assertThat(event.domain()).isEqualTo("fastfood");
            assertThat(event.metadata().get("eventName")).isEqualTo(type);
            assertThat(event.payload()).containsKeys("brand", "brandName", "storeId", "storeCity", "country");
            assertThat(FastFoodBrand.from((String) event.payload().get("brand"))).isPresent();
        }
    }

    @RepeatedTest(5)
    void orderEventsCarryItemsAndConsistentTotal() {
        Event event = generator.generateEvent(FastFoodGenerator.ORDER_PLACED);
        assertThat(event.payload()).containsKeys("orderId", "channel", "items", "itemCount",
                "totalAmount", "paymentMethod", "orderStatus", "employeeId");
        assertThat(event.payload().get("orderStatus")).isEqualTo("PLACED");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) event.payload().get("items");
        assertThat(items).isNotEmpty();
        assertThat(event.payload().get("itemCount")).isEqualTo(items.size());

        double expected = items.stream()
                .mapToDouble(item -> ((Number) item.get("lineTotal")).doubleValue())
                .sum();
        double total = ((Number) event.payload().get("totalAmount")).doubleValue();
        assertThat(total).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void inventoryEventsCarryIngredientOfTheBrand() {
        Event event = generator.generateEvent(FastFoodGenerator.INVENTORY_UPDATED);
        assertThat(event.payload()).containsKeys("ingredientSku", "ingredientName", "unit",
                "quantityOnHand", "reorderLevel");
        FastFoodBrand brand = FastFoodBrand.from((String) event.payload().get("brand")).orElseThrow();
        assertThat(brand.ingredients().stream().map(FastFoodBrand.Ingredient::sku))
                .contains((String) event.payload().get("ingredientSku"));
    }

    @RepeatedTest(5)
    void generatesErrorEventsWithMetadata() {
        Event event = generator.generateErrorEvent();
        assertThat(event.eventType()).isEqualTo("ERROR");
        assertThat(event.metadata()).containsKeys("errorType", "errorCode", "errorMessage", "severity");
        assertThat(generator.getErrorTypes()).contains(event.metadata().get("errorType"));
    }

    @Test
    void generatesEachErrorType() {
        for (String type : generator.getErrorTypes()) {
            Event event = generator.generateEvent(type);
            assertThat(event.eventType()).isEqualTo("ERROR");
            assertThat(event.metadata().get("errorType")).isEqualTo(type);
            assertThat(event.payload()).containsKey("brand");
        }
    }

    @Test
    void kitchenDelayExceedsSla() {
        Event event = generator.generateEvent(FastFoodGenerator.ERR_KITCHEN_DELAY);
        int prep = ((Number) event.payload().get("prepTimeSeconds")).intValue();
        int sla = ((Number) event.payload().get("slaSeconds")).intValue();
        assertThat(prep).isGreaterThan(sla);
        assertThat(event.metadata().get("severity")).isEqualTo("MEDIUM");
    }

    @Test
    void coldChainBreachIsCriticalAndAboveThreshold() {
        Event event = generator.generateEvent(FastFoodGenerator.ERR_COLD_CHAIN_BREACH);
        double temperature = ((Number) event.payload().get("temperatureCelsius")).doubleValue();
        double threshold = ((Number) event.payload().get("thresholdCelsius")).doubleValue();
        assertThat(temperature).isGreaterThan(threshold);
        assertThat(event.metadata().get("severity")).isEqualTo("CRITICAL");
    }

    @Test
    void ingredientOutOfStockLeavesNoStockAndBlocksItems() {
        Event event = generator.generateEvent(FastFoodGenerator.ERR_INGREDIENT_OUT_OF_STOCK);
        assertThat(((Number) event.payload().get("quantityOnHand")).doubleValue()).isZero();
        assertThat((List<?>) event.payload().get("blockedItems")).isNotEmpty();
    }

    @Test
    void driveThruTimeoutExceedsThreshold() {
        Event event = generator.generateEvent(FastFoodGenerator.ERR_DRIVE_THRU_TIMEOUT);
        int wait = ((Number) event.payload().get("waitTimeSeconds")).intValue();
        int threshold = ((Number) event.payload().get("timeoutThresholdSeconds")).intValue();
        assertThat(event.payload().get("channel")).isEqualTo("DRIVE_THRU");
        assertThat(wait).isGreaterThan(threshold);
    }

    @Test
    void generatorCanBeRestrictedToASingleFranchise() {
        FastFoodGenerator onlyBurgerKing = new FastFoodGenerator(List.of(FastFoodBrand.BURGER_KING));
        for (int i = 0; i < 20; i++) {
            Event event = onlyBurgerKing.generateEvent(FastFoodGenerator.APP_ORDER);
            assertThat(event.payload().get("brand")).isEqualTo("BURGER_KING");
            assertThat((String) event.payload().get("storeId")).startsWith("BK-");
        }
    }

    @Test
    void emptyBrandListIsRejected() {
        assertThatThrownBy(() -> new FastFoodGenerator(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void domainIsDiscoveredViaServiceLoader() {
        de.omnistreamforce.domain.DomainRegistry registry = new de.omnistreamforce.domain.DomainRegistry();
        assertThat(registry.isAvailable("fastfood")).isTrue();
        assertThat(registry.get("fastfood")).containsInstanceOf(FastFoodGenerator.class);
    }

    @Test
    void unsupportedEventTypeThrows() {
        assertThatThrownBy(() -> generator.generateEvent("Whatever"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
