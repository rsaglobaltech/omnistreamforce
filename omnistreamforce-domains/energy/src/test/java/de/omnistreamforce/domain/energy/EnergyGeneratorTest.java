package de.omnistreamforce.domain.energy;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.DomainRegistry;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnergyGeneratorTest {

    private final EnergyGenerator generator = new EnergyGenerator();

    private double number(Event event, String field) {
        return ((Number) event.payload().get(field)).doubleValue();
    }

    @Test
    void schemaIsComplete() {
        assertThat(generator.getDomainName()).isEqualTo("energy");
        assertThat(generator.getSupportedEventTypes()).hasSize(4);
        assertThat(generator.getErrorTypes()).hasSize(3);
        assertThat(generator.getSchema().eventTypes()).hasSize(7);
        assertThat(generator.getSchema().errorSpec().errorRate()).isEqualTo(0.08);
    }

    @Test
    void generatesEachNormalEventType() {
        for (String type : generator.getSupportedEventTypes()) {
            Event event = generator.generateEvent(type);
            assertThat(event.eventType()).isEqualTo("NORMAL");
            assertThat(event.domain()).isEqualTo("energy");
            assertThat(event.metadata().get("eventName")).isEqualTo(type);
            assertThat(event.payload()).isNotEmpty();
        }
    }

    @RepeatedTest(5)
    void powerGenerationUsesRangesTypicalOfItsSource() {
        Event event = generator.generateEvent(EnergyGenerator.POWER_GENERATION);

        assertThat(event.payload()).containsKeys("plantId", "source", "outputMW", "efficiency");
        assertThat((String) event.payload().get("plantId")).startsWith("PLANT-");
        assertThat(number(event, "outputMW")).isBetween(0.0, 900.0);
        assertThat(number(event, "efficiency")).isBetween(0.15, 0.95);

        // la nuclear no baja de 400 MW; la solar no pasa de 180
        String source = (String) event.payload().get("source");
        if ("NUCLEAR".equals(source)) {
            assertThat(number(event, "outputMW")).isGreaterThanOrEqualTo(400.0);
        }
        if ("SOLAR".equals(source)) {
            assertThat(number(event, "outputMW")).isLessThanOrEqualTo(180.0);
        }
    }

    @RepeatedTest(10)
    void aHealthyGridStaysWithinTheFrequencyMargin() {
        Event event = generator.generateEvent(EnergyGenerator.GRID_STATUS);

        double deviation = Math.abs(number(event, "frequency") - EnergyGenerator.NOMINAL_FREQUENCY_HZ);
        assertThat(deviation).isLessThan(EnergyGenerator.FREQUENCY_THRESHOLD_HZ);
        assertThat(event.payload().get("stability")).isIn("STABLE", "DEGRADED");
    }

    @Test
    void meterReadingsIdentifyMeterAndCustomer() {
        Event event = generator.generateEvent(EnergyGenerator.METER_READING);

        assertThat((String) event.payload().get("meterId")).startsWith("MTR-");
        assertThat((String) event.payload().get("customerId")).startsWith("CUST-");
        assertThat(number(event, "consumptionKWh")).isPositive();
    }

    @Test
    void priceUpdatesMayGoNegativeAsInTheRealMarket() {
        boolean anyNegativeAllowed = false;
        for (int i = 0; i < 200; i++) {
            Event event = generator.generateEvent(EnergyGenerator.PRICE_UPDATE);
            assertThat(number(event, "pricePerMWh")).isBetween(-20.0, 350.0);
            assertThat(event.payload().get("currency")).isIn("EUR", "USD", "GBP");
            anyNegativeAllowed |= number(event, "pricePerMWh") < 0;
        }
        assertThat(anyNegativeAllowed).isTrue();
    }

    @RepeatedTest(5)
    void generatesErrorEventsWithMetadata() {
        Event event = generator.generateErrorEvent();

        assertThat(event.eventType()).isEqualTo("ERROR");
        assertThat(event.metadata()).containsKeys("errorType", "errorCode", "errorMessage", "severity");
        assertThat(generator.getErrorTypes()).contains(event.metadata().get("errorType"));
    }

    @RepeatedTest(10)
    void aFrequencyDeviationAlwaysExceedsTheThreshold() {
        Event event = generator.generateEvent(EnergyGenerator.ERR_GRID_FREQUENCY_DEVIATION);

        double frequency = number(event, "frequency");
        double deviation = Math.abs(frequency - EnergyGenerator.NOMINAL_FREQUENCY_HZ);
        assertThat(deviation).isGreaterThan(number(event, "threshold"));
        assertThat(number(event, "deviation")).isGreaterThan(EnergyGenerator.FREQUENCY_THRESHOLD_HZ);
        assertThat(event.payload().get("stability")).isEqualTo("CRITICAL");
    }

    @Test
    void anOutageStopsProductionAndListsAffectedAreas() {
        Event event = generator.generateEvent(EnergyGenerator.ERR_POWER_OUTAGE);

        assertThat(number(event, "outputMW")).isZero();
        assertThat((List<?>) event.payload().get("affectedAreas")).isNotEmpty();
        assertThat(event.payload()).containsKeys("cause", "affectedCustomers", "estimatedRestoreMinutes");
        assertThat(event.metadata().get("severity")).isEqualTo("CRITICAL");
    }

    @Test
    void anEquipmentFailureDegradesTheOutput() {
        Event event = generator.generateEvent(EnergyGenerator.ERR_EQUIPMENT_FAILURE);

        assertThat(event.payload()).containsKeys("equipmentType", "failureType", "maintenanceRequired");
        assertThat(number(event, "efficiency")).isLessThan(0.5);
        assertThat(event.payload().get("maintenanceRequired")).isEqualTo(true);
    }

    @Test
    void domainIsDiscoveredViaServiceLoader() {
        DomainRegistry registry = new DomainRegistry();
        assertThat(registry.isAvailable("energy")).isTrue();
        assertThat(registry.get("energy")).containsInstanceOf(EnergyGenerator.class);
    }

    @Test
    void unsupportedEventTypeThrows() {
        assertThatThrownBy(() -> generator.generateEvent("Whatever"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
