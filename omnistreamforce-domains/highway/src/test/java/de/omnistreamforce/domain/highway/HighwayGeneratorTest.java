package de.omnistreamforce.domain.highway;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.DomainRegistry;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HighwayGeneratorTest {

    private final HighwayGenerator generator = new HighwayGenerator();

    private double number(Event event, String field) {
        return ((Number) event.payload().get(field)).doubleValue();
    }

    @Test
    void schemaIsComplete() {
        assertThat(generator.getDomainName()).isEqualTo("highway");
        assertThat(generator.getSupportedEventTypes()).hasSize(4);
        assertThat(generator.getErrorTypes()).hasSize(3);
        assertThat(generator.getSchema().eventTypes()).hasSize(7);
        assertThat(generator.getSchema().errorSpec().errorRate()).isEqualTo(0.12);
    }

    @Test
    void everyEventIdentifiesTheSegment() {
        for (String type : generator.getSupportedEventTypes()) {
            Event event = generator.generateEvent(type);
            assertThat(event.eventType()).isEqualTo("NORMAL");
            assertThat(event.domain()).isEqualTo("highway");
            assertThat((String) event.payload().get("segmentId")).startsWith("SEG-");
        }
    }

    @RepeatedTest(10)
    void trafficFlowRelatesDensityToSpeed() {
        Event event = generator.generateEvent(HighwayGenerator.TRAFFIC_FLOW);

        double density = number(event, "density");
        double avgSpeed = number(event, "avgSpeed");

        // el trafico fluido nunca baja del umbral de retencion
        assertThat(avgSpeed).isGreaterThan(HighwayGenerator.JAM_SPEED_THRESHOLD_KMH);
        assertThat(avgSpeed).isLessThanOrEqualTo(HighwayGenerator.SPEED_LIMIT_KMH);
        // a mayor densidad, menor velocidad
        if (density > 40) {
            assertThat(avgSpeed).isLessThan(HighwayGenerator.SPEED_LIMIT_KMH - 30);
        }
        assertThat(number(event, "occupancyPercent")).isLessThanOrEqualTo(95.0);
    }

    @RepeatedTest(10)
    void aNormalPassageRespectsTheSpeedLimit() {
        Event event = generator.generateEvent(HighwayGenerator.VEHICLE_PASSAGE);

        assertThat(number(event, "speed")).isLessThanOrEqualTo(HighwayGenerator.SPEED_LIMIT_KMH);
        assertThat(number(event, "lane")).isBetween(1.0, 4.0);
        assertThat(event.payload().get("vehicleType"))
                .isIn("CAR", "TRUCK", "MOTORCYCLE", "BUS", "VAN");
    }

    @Test
    void incidentReportsCarryTheirIdentifierAndImpact() {
        Event event = generator.generateEvent(HighwayGenerator.INCIDENT_REPORT);

        assertThat((String) event.payload().get("incidentId")).startsWith("INC-");
        assertThat(event.payload()).containsKeys("incidentType", "lanesAffected", "estimatedClearMinutes");
        assertThat(number(event, "lanesAffected")).isBetween(1.0, 3.0);
    }

    @RepeatedTest(10)
    void tollFaresDependOnTheVehicleClass() {
        Event event = generator.generateEvent(HighwayGenerator.TOLL_PAYMENT);

        String vehicleClass = (String) event.payload().get("vehicleClass");
        double amount = number(event, "amount");

        assertThat((String) event.payload().get("tollId")).startsWith("TOLL-");
        switch (vehicleClass) {
            case "EXEMPT" -> assertThat(amount).isZero();
            case "HEAVY" -> assertThat(amount).isGreaterThanOrEqualTo(8.0);
            case "MOTORCYCLE" -> assertThat(amount).isLessThanOrEqualTo(6.0);
            default -> assertThat(amount).isPositive();
        }
    }

    @RepeatedTest(5)
    void generatesErrorEventsWithMetadata() {
        Event event = generator.generateErrorEvent();

        assertThat(event.eventType()).isEqualTo("ERROR");
        assertThat(event.metadata()).containsKeys("errorType", "errorCode", "errorMessage", "severity");
        assertThat(generator.getErrorTypes()).contains(event.metadata().get("errorType"));
    }

    @RepeatedTest(10)
    void aTrafficJamIsAlwaysBelowTheSpeedThreshold() {
        Event event = generator.generateEvent(HighwayGenerator.ERR_TRAFFIC_JAM);

        assertThat(number(event, "avgSpeed")).isLessThan(number(event, "threshold"));
        assertThat(number(event, "avgSpeed")).isLessThan(HighwayGenerator.JAM_SPEED_THRESHOLD_KMH);
        assertThat(number(event, "vehicleCount")).isGreaterThan(100);
        assertThat(number(event, "queueLengthKm")).isPositive();
    }

    @RepeatedTest(10)
    void aSpeedViolationAlwaysExceedsTheLimitAndMatchesTheExcess() {
        Event event = generator.generateEvent(HighwayGenerator.ERR_SPEED_VIOLATION);

        double speed = number(event, "speed");
        double limit = number(event, "limit");

        assertThat(speed).isGreaterThan(limit);
        assertThat(number(event, "excessKmh")).isCloseTo(speed - limit,
                org.assertj.core.data.Offset.offset(0.01));
        if (speed > limit * 1.5) {
            assertThat(event.payload().get("violationType")).isEqualTo("SEVERE");
        } else {
            assertThat(event.payload().get("violationType")).isEqualTo("MINOR");
        }
    }

    @Test
    void aBrokenSensorReportsNoVehicles() {
        Event event = generator.generateEvent(HighwayGenerator.ERR_SENSOR_MALFUNCTION);

        assertThat((String) event.payload().get("sensorId")).startsWith("SNS-");
        assertThat(number(event, "vehicleCount")).isZero();
        assertThat(event.payload()).containsKeys("malfunctionType", "lastReadingMinutesAgo",
                "maintenanceTicket");
        assertThat(event.metadata().get("severity")).isEqualTo("LOW");
    }

    @Test
    void domainIsDiscoveredViaServiceLoader() {
        DomainRegistry registry = new DomainRegistry();
        assertThat(registry.isAvailable("highway")).isTrue();
        assertThat(registry.get("highway")).containsInstanceOf(HighwayGenerator.class);
    }

    @Test
    void unsupportedEventTypeThrows() {
        assertThatThrownBy(() -> generator.generateEvent("Whatever"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
