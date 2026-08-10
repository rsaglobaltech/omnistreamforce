package de.omnistreamforce.domain.autos;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.DomainRegistry;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutosGeneratorTest {

    private final AutosGenerator generator = new AutosGenerator();

    private double number(Event event, String field) {
        return ((Number) event.payload().get(field)).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Event event, String field) {
        return (Map<String, Object>) event.payload().get(field);
    }

    @Test
    void schemaIsComplete() {
        assertThat(generator.getDomainName()).isEqualTo("autos");
        assertThat(generator.getSupportedEventTypes()).hasSize(4);
        assertThat(generator.getErrorTypes()).hasSize(4);
        assertThat(generator.getSchema().eventTypes()).hasSize(8);
        assertThat(generator.getSchema().errorSpec().errorRate()).isEqualTo(0.1);
    }

    @Test
    void everyEventIdentifiesTheVehicle() {
        for (String type : generator.getSupportedEventTypes()) {
            Event event = generator.generateEvent(type);
            assertThat(event.eventType()).isEqualTo("NORMAL");
            assertThat(event.domain()).isEqualTo("autos");
            assertThat((String) event.payload().get("vehicleId")).startsWith("VEH-");
            assertThat(event.payload()).containsKeys("plate", "model");
        }
    }

    @RepeatedTest(10)
    void telemetryKeepsRpmConsistentWithSpeed() {
        Event event = generator.generateEvent(AutosGenerator.VEHICLE_TELEMETRY);

        double speed = number(event, "speed");
        double rpm = number(event, "rpm");
        if (speed < 1) {
            assertThat(rpm).isZero();
        } else {
            assertThat(rpm).isGreaterThan(700);
        }
        // un motor sano nunca alcanza el umbral de sobrecalentamiento
        assertThat(number(event, "engineTemp")).isLessThan(AutosGenerator.ENGINE_TEMP_THRESHOLD_C);
        assertThat(map(event, "gpsLocation")).containsKeys("latitude", "longitude");
    }

    @RepeatedTest(5)
    void aParkedVehicleHasNoSpeed() {
        for (int i = 0; i < 20; i++) {
            Event event = generator.generateEvent(AutosGenerator.VEHICLE_STATUS);
            if (!"MOVING".equals(event.payload().get("status"))) {
                assertThat(number(event, "speed")).isZero();
            }
            assertThat(number(event, "batteryLevel"))
                    .isGreaterThan(AutosGenerator.BATTERY_CRITICAL_THRESHOLD);
            assertThat(map(event, "tirePressure"))
                    .containsKeys("frontLeft", "frontRight", "rearLeft", "rearRight");
        }
    }

    @RepeatedTest(10)
    void tripDurationIsCoherentWithDistance() {
        Event event = generator.generateEvent(AutosGenerator.TRIP_EVENT);

        double distance = number(event, "distanceKm");
        double minutes = number(event, "durationMinutes");
        double averageSpeed = distance / (minutes / 60.0);

        assertThat((String) event.payload().get("tripId")).startsWith("TRIP-");
        assertThat(minutes).isPositive();
        assertThat(averageSpeed).isBetween(10.0, 140.0);
        assertThat(map(event, "startLocation")).containsKey("latitude");
        assertThat(map(event, "endLocation")).containsKey("longitude");
    }

    @Test
    void maintenanceAlertsNameTheComponent() {
        Event event = generator.generateEvent(AutosGenerator.MAINTENANCE_ALERT);

        assertThat(event.payload().get("component")).isIn("BRAKES", "TIRES", "OIL", "BATTERY",
                "AIR_FILTER", "TRANSMISSION");
        assertThat(event.payload()).containsKeys("alertType", "odometer", "dueInKm");
    }

    @RepeatedTest(5)
    void generatesErrorEventsWithMetadata() {
        Event event = generator.generateErrorEvent();

        assertThat(event.eventType()).isEqualTo("ERROR");
        assertThat(event.metadata()).containsKeys("errorType", "errorCode", "errorMessage", "severity");
        assertThat(generator.getErrorTypes()).contains(event.metadata().get("errorType"));
    }

    @RepeatedTest(10)
    void overheatAlwaysExceedsTheThreshold() {
        Event event = generator.generateEvent(AutosGenerator.ERR_ENGINE_OVERHEAT);

        assertThat(number(event, "engineTemp")).isGreaterThan(number(event, "threshold"));
        assertThat(number(event, "engineTemp"))
                .isGreaterThan(AutosGenerator.ENGINE_TEMP_THRESHOLD_C);
        assertThat(event.metadata().get("severity")).isEqualTo("HIGH");
    }

    @RepeatedTest(10)
    void criticalBatteryIsAlwaysBelowTheThreshold() {
        Event event = generator.generateEvent(AutosGenerator.ERR_BATTERY_CRITICAL);

        assertThat(number(event, "batteryLevel")).isLessThan(number(event, "threshold"));
        assertThat(number(event, "estimatedRangeKm")).isLessThanOrEqualTo(25.0);
    }

    @Test
    void brakeFailureIsCriticalAndImmobilizes() {
        Event event = generator.generateEvent(AutosGenerator.ERR_BRAKE_FAILURE);

        assertThat(event.metadata().get("severity")).isEqualTo("CRITICAL");
        assertThat(event.payload().get("immobilizeRequired")).isEqualTo(true);
        assertThat(number(event, "padWearPercent")).isGreaterThanOrEqualTo(85.0);
    }

    @Test
    void gpsLossKeepsTheLastKnownPositionButNoCurrentOne() {
        Event event = generator.generateEvent(AutosGenerator.ERR_GPS_LOSS);

        assertThat(map(event, "lastKnownLocation")).containsKeys("latitude", "longitude");
        assertThat(event.payload().get("gpsLocation")).isNull();
        assertThat(number(event, "satellitesInView")).isLessThanOrEqualTo(3);
    }

    @Test
    void domainIsDiscoveredViaServiceLoader() {
        DomainRegistry registry = new DomainRegistry();
        assertThat(registry.isAvailable("autos")).isTrue();
        assertThat(registry.get("autos")).containsInstanceOf(AutosGenerator.class);
    }

    @Test
    void unsupportedEventTypeThrows() {
        assertThatThrownBy(() -> generator.generateEvent("Whatever"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
