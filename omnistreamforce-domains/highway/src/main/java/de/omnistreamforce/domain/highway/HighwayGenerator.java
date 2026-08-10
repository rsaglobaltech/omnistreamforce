package de.omnistreamforce.domain.highway;

import de.omnistreamforce.core.ErrorSpec;
import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.EventSpec;
import de.omnistreamforce.core.EventType;
import de.omnistreamforce.core.FieldDefinition;
import de.omnistreamforce.core.Severity;
import de.omnistreamforce.domain.AbstractDomainGenerator;
import de.omnistreamforce.util.RandomUtils;
import net.datafaker.Faker;

import java.util.List;
import java.util.Map;

/**
 * Dominio de autopistas: trafico, pasos de vehiculo, incidentes y peajes.
 */
public class HighwayGenerator extends AbstractDomainGenerator {

    public static final String DOMAIN = "highway";

    public static final String TRAFFIC_FLOW = "TrafficFlow";
    public static final String VEHICLE_PASSAGE = "VehiclePassage";
    public static final String INCIDENT_REPORT = "IncidentReport";
    public static final String TOLL_PAYMENT = "TollPayment";

    public static final String ERR_TRAFFIC_JAM = "TrafficJam";
    public static final String ERR_SPEED_VIOLATION = "SpeedViolation";
    public static final String ERR_SENSOR_MALFUNCTION = "SensorMalfunction";

    /** Limite de velocidad del tramo. */
    public static final int SPEED_LIMIT_KMH = 120;
    /** Velocidad media por debajo de la cual el tramo se considera atascado. */
    public static final double JAM_SPEED_THRESHOLD_KMH = 20.0;

    private static final List<String> NORMAL_TYPES =
            List.of(TRAFFIC_FLOW, VEHICLE_PASSAGE, INCIDENT_REPORT, TOLL_PAYMENT);
    private static final List<String> ERROR_TYPES =
            List.of(ERR_TRAFFIC_JAM, ERR_SPEED_VIOLATION, ERR_SENSOR_MALFUNCTION);

    private static final List<String> VEHICLE_TYPES = List.of("CAR", "TRUCK", "MOTORCYCLE", "BUS", "VAN");
    private static final List<String> VEHICLE_CLASSES = List.of("LIGHT", "HEAVY", "MOTORCYCLE", "EXEMPT");
    private static final List<String> INCIDENT_TYPES = List.of("ACCIDENT", "CONSTRUCTION", "WEATHER",
            "BROKEN_DOWN_VEHICLE", "DEBRIS");
    private static final List<String> PAYMENT_METHODS = List.of("CASH", "CARD", "TAG", "APP", "PLATE_RECOGNITION");
    private static final List<String> MALFUNCTIONS = List.of("NO_SIGNAL", "OUT_OF_RANGE",
            "CALIBRATION_DRIFT", "POWER_LOSS", "FIRMWARE_ERROR");

    private final Faker faker = new Faker();

    @Override
    public String getDomainName() {
        return DOMAIN;
    }

    @Override
    public List<String> getSupportedEventTypes() {
        return NORMAL_TYPES;
    }

    @Override
    public List<String> getErrorTypes() {
        return ERROR_TYPES;
    }

    @Override
    public EventSchema getSchema() {
        List<FieldDefinition> fields = List.of(
                fd("segmentId", "string", true, "regex SEG-[0-9]{4}", "Tramo de la autopista"),
                fd("vehicleCount", "int", false, null, "Vehiculos contados en el tramo"),
                fd("avgSpeed", "double", false, null, "Velocidad media en km/h"),
                fd("density", "double", false, null, "Densidad en vehiculos por kilometro"),
                fd("plate", "string", false, "Vehicle.licensePlate", "Matricula"),
                fd("vehicleType", "enum", false, null, VEHICLE_TYPES, "Tipo de vehiculo"),
                fd("speed", "double", false, null, "Velocidad del vehiculo"),
                fd("lane", "int", false, null, "Carril"),
                fd("incidentId", "string", false, "regex INC-[0-9]{6}", "Identificador del incidente"),
                fd("incidentType", "enum", false, null, INCIDENT_TYPES, "Tipo de incidente"),
                fd("lanesAffected", "int", false, null, "Carriles afectados"),
                fd("tollId", "string", false, "regex TOLL-[0-9]{3}", "Peaje"),
                fd("amount", "double", false, null, "Importe del peaje"),
                fd("paymentMethod", "enum", false, null, PAYMENT_METHODS, "Forma de pago"),
                fd("vehicleClass", "enum", false, null, VEHICLE_CLASSES, "Clase tarifaria"),
                fd("sensorId", "string", false, "regex SNS-[0-9]{5}", "Sensor del tramo")
        );

        return new EventSchema(
                DOMAIN,
                "HighwayEvents",
                "Eventos de autopista: flujo de trafico, pasos de vehiculo, incidentes y peajes.",
                fields,
                List.of(
                        new EventSpec(TRAFFIC_FLOW, EventType.NORMAL, "Medida de trafico de un tramo", fields),
                        new EventSpec(VEHICLE_PASSAGE, EventType.NORMAL, "Paso de un vehiculo", fields),
                        new EventSpec(INCIDENT_REPORT, EventType.NORMAL, "Parte de incidencia", fields),
                        new EventSpec(TOLL_PAYMENT, EventType.NORMAL, "Cobro de peaje", fields),
                        new EventSpec(ERR_TRAFFIC_JAM, EventType.ERROR, "Retencion en el tramo", fields),
                        new EventSpec(ERR_SPEED_VIOLATION, EventType.ERROR, "Exceso de velocidad", fields),
                        new EventSpec(ERR_SENSOR_MALFUNCTION, EventType.ERROR, "Sensor averiado", fields)
                ),
                new ErrorSpec(ERROR_TYPES, 0.12,
                        List.of("avgSpeed", "speed", "vehicleCount", "sensorId"))
        );
    }

    @Override
    public Event generateEvent(String eventType) {
        return switch (eventType) {
            case TRAFFIC_FLOW -> trafficFlow();
            case VEHICLE_PASSAGE -> vehiclePassage();
            case INCIDENT_REPORT -> incidentReport();
            case TOLL_PAYMENT -> tollPayment();
            case ERR_TRAFFIC_JAM, ERR_SPEED_VIOLATION,
                 ERR_SENSOR_MALFUNCTION -> generateErrorEvent(eventType);
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + eventType);
        };
    }

    @Override
    public Event generateErrorEvent() {
        return generateErrorEvent(RandomUtils.randomFrom(ERROR_TYPES));
    }

    private Event generateErrorEvent(String errorType) {
        return switch (errorType) {
            case ERR_TRAFFIC_JAM -> buildErrorEvent(DOMAIN, ERR_TRAFFIC_JAM,
                    "Retencion: velocidad media por debajo del umbral",
                    Severity.MEDIUM, trafficJamPayload());
            case ERR_SPEED_VIOLATION -> buildErrorEvent(DOMAIN, ERR_SPEED_VIOLATION,
                    "Vehiculo circulando por encima del limite",
                    Severity.HIGH, speedViolationPayload());
            case ERR_SENSOR_MALFUNCTION -> buildErrorEvent(DOMAIN, ERR_SENSOR_MALFUNCTION,
                    "Sensor de tramo averiado",
                    Severity.LOW, sensorMalfunctionPayload());
            default -> throw new IllegalArgumentException("Tipo de error no soportado: " + errorType);
        };
    }

    // --- eventos normales ----------------------------------------------------

    private Event trafficFlow() {
        // a mas densidad, menos velocidad: la relacion es lo que hace creible el dato
        double density = round(RandomUtils.nextDouble(2.0, 55.0));
        double avgSpeed = round(Math.max(JAM_SPEED_THRESHOLD_KMH + 1,
                SPEED_LIMIT_KMH - density * RandomUtils.nextDouble(1.0, 1.6)));
        Map<String, Object> payload = payload(
                "segmentId", segmentId(),
                "vehicleCount", (int) Math.round(density * RandomUtils.nextDouble(1.0, 4.0)),
                "avgSpeed", avgSpeed,
                "density", density,
                "occupancyPercent", round(Math.min(95.0, density * 1.7)),
                "direction", RandomUtils.randomFrom("NORTH", "SOUTH", "EAST", "WEST"));
        return buildNormalEvent(DOMAIN, TRAFFIC_FLOW, payload);
    }

    private Event vehiclePassage() {
        Map<String, Object> payload = payload(
                "segmentId", segmentId(),
                "plate", faker.vehicle().licensePlate(),
                "vehicleType", RandomUtils.randomFrom(VEHICLE_TYPES),
                // dentro del limite: los excesos son evento de error
                "speed", round(RandomUtils.nextDouble(60.0, SPEED_LIMIT_KMH)),
                "lane", RandomUtils.nextInt(1, 4),
                "axles", RandomUtils.nextInt(2, 6));
        return buildNormalEvent(DOMAIN, VEHICLE_PASSAGE, payload);
    }

    private Event incidentReport() {
        int lanes = RandomUtils.nextInt(1, 3);
        Map<String, Object> payload = payload(
                "incidentId", "INC-" + RandomUtils.nextInt(100000, 999999),
                "segmentId", segmentId(),
                "incidentType", RandomUtils.randomFrom(INCIDENT_TYPES),
                "severity", RandomUtils.randomFrom("LOW", "MEDIUM", "HIGH"),
                "lanesAffected", lanes,
                "location", faker.address().cityName(),
                "estimatedClearMinutes", RandomUtils.nextInt(10, 240));
        return buildNormalEvent(DOMAIN, INCIDENT_REPORT, payload);
    }

    private Event tollPayment() {
        String vehicleClass = RandomUtils.randomFrom(VEHICLE_CLASSES);
        Map<String, Object> payload = payload(
                "tollId", "TOLL-" + RandomUtils.nextInt(100, 999),
                "segmentId", segmentId(),
                "plate", faker.vehicle().licensePlate(),
                "vehicleClass", vehicleClass,
                "amount", amountFor(vehicleClass),
                "currency", "EUR",
                "paymentMethod", RandomUtils.randomFrom(PAYMENT_METHODS));
        return buildNormalEvent(DOMAIN, TOLL_PAYMENT, payload);
    }

    // --- payloads de error ---------------------------------------------------

    private Map<String, Object> trafficJamPayload() {
        return payload(
                "segmentId", segmentId(),
                // por debajo del umbral: eso es lo que define la retencion
                "avgSpeed", round(RandomUtils.nextDouble(1.0, JAM_SPEED_THRESHOLD_KMH - 0.1)),
                "threshold", JAM_SPEED_THRESHOLD_KMH,
                "vehicleCount", RandomUtils.nextInt(180, 900),
                "density", round(RandomUtils.nextDouble(60.0, 140.0)),
                "queueLengthKm", round(RandomUtils.nextDouble(0.5, 12.0)));
    }

    private Map<String, Object> speedViolationPayload() {
        double speed = round(RandomUtils.nextDouble(SPEED_LIMIT_KMH + 1, 220.0));
        return payload(
                "segmentId", segmentId(),
                "plate", faker.vehicle().licensePlate(),
                "vehicleType", RandomUtils.randomFrom(VEHICLE_TYPES),
                "speed", speed,
                "limit", SPEED_LIMIT_KMH,
                "excessKmh", round(speed - SPEED_LIMIT_KMH),
                "violationType", speed > SPEED_LIMIT_KMH * 1.5 ? "SEVERE" : "MINOR",
                "lane", RandomUtils.nextInt(1, 4));
    }

    private Map<String, Object> sensorMalfunctionPayload() {
        return payload(
                "sensorId", "SNS-" + RandomUtils.nextInt(10000, 99999),
                "segmentId", segmentId(),
                "malfunctionType", RandomUtils.randomFrom(MALFUNCTIONS),
                "lastReadingMinutesAgo", RandomUtils.nextInt(5, 1440),
                "vehicleCount", 0,
                "maintenanceTicket", "TCK-" + RandomUtils.nextInt(10000, 99999));
    }

    // --- helpers -------------------------------------------------------------

    /** Los pesados pagan mas y los exentos no pagan. */
    private double amountFor(String vehicleClass) {
        return switch (vehicleClass) {
            case "HEAVY" -> round(RandomUtils.nextDouble(8.0, 45.0));
            case "MOTORCYCLE" -> round(RandomUtils.nextDouble(1.0, 6.0));
            case "EXEMPT" -> 0.0;
            default -> round(RandomUtils.nextDouble(2.0, 18.0));
        };
    }

    private String segmentId() {
        return "SEG-" + RandomUtils.nextInt(1000, 9999);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private FieldDefinition fd(String name, String type, boolean required, String fakerExpression,
                               String description) {
        return new FieldDefinition(name, type, required, fakerExpression, null, null, description);
    }

    private FieldDefinition fd(String name, String type, boolean required, String fakerExpression,
                               List<String> enumValues, String description) {
        return new FieldDefinition(name, type, required, fakerExpression, null, enumValues, description);
    }
}
