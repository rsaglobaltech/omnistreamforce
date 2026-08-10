package de.omnistreamforce.domain.autos;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dominio de automocion: telemetria de vehiculos, estado, viajes y avisos de mantenimiento.
 */
public class AutosGenerator extends AbstractDomainGenerator {

    public static final String DOMAIN = "autos";

    public static final String VEHICLE_TELEMETRY = "VehicleTelemetry";
    public static final String VEHICLE_STATUS = "VehicleStatus";
    public static final String TRIP_EVENT = "TripEvent";
    public static final String MAINTENANCE_ALERT = "MaintenanceAlert";

    public static final String ERR_ENGINE_OVERHEAT = "EngineOverheat";
    public static final String ERR_BRAKE_FAILURE = "BrakeFailure";
    public static final String ERR_BATTERY_CRITICAL = "BatteryCritical";
    public static final String ERR_GPS_LOSS = "GPSLoss";

    /** Temperatura de motor a partir de la cual se considera sobrecalentamiento. */
    public static final double ENGINE_TEMP_THRESHOLD_C = 110.0;
    /** Nivel de bateria por debajo del cual la situacion es critica. */
    public static final double BATTERY_CRITICAL_THRESHOLD = 10.0;

    private static final List<String> NORMAL_TYPES =
            List.of(VEHICLE_TELEMETRY, VEHICLE_STATUS, TRIP_EVENT, MAINTENANCE_ALERT);
    private static final List<String> ERROR_TYPES =
            List.of(ERR_ENGINE_OVERHEAT, ERR_BRAKE_FAILURE, ERR_BATTERY_CRITICAL, ERR_GPS_LOSS);

    private static final List<String> STATUSES = List.of("MOVING", "PARKED", "CHARGING", "MAINTENANCE");
    private static final List<String> COMPONENTS = List.of("BRAKES", "TIRES", "OIL", "BATTERY",
            "AIR_FILTER", "TRANSMISSION");
    private static final List<String> ALERT_TYPES = List.of("SCHEDULED", "WEAR", "URGENT", "RECALL");

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
                fd("vehicleId", "string", true, "regex VEH-[0-9]{6}", "Identificador del vehiculo"),
                fd("plate", "string", false, "Vehicle.licensePlate", "Matricula"),
                fd("model", "string", false, "Vehicle.model", "Modelo"),
                fd("speed", "double", false, null, "Velocidad en km/h"),
                fd("rpm", "int", false, null, "Revoluciones por minuto"),
                fd("fuelLevel", "double", false, null, "Nivel de combustible en porcentaje"),
                fd("engineTemp", "double", false, null, "Temperatura del motor en grados"),
                fd("gpsLocation", "object", false, null, "Posicion GPS"),
                fd("odometer", "double", false, null, "Kilometros totales"),
                fd("status", "enum", false, null, STATUSES, "Estado del vehiculo"),
                fd("batteryLevel", "double", false, null, "Nivel de bateria en porcentaje"),
                fd("tirePressure", "object", false, null, "Presion de cada neumatico"),
                fd("tripId", "string", false, "regex TRIP-[0-9]{8}", "Identificador del viaje"),
                fd("distanceKm", "double", false, null, "Distancia recorrida"),
                fd("durationMinutes", "int", false, null, "Duracion del viaje"),
                fd("component", "enum", false, null, COMPONENTS, "Componente afectado"),
                fd("alertType", "enum", false, null, ALERT_TYPES, "Tipo de aviso")
        );

        return new EventSchema(
                DOMAIN,
                "AutosEvents",
                "Eventos de flota: telemetria, estado, viajes y mantenimiento de vehiculos.",
                fields,
                List.of(
                        new EventSpec(VEHICLE_TELEMETRY, EventType.NORMAL, "Telemetria periodica", fields),
                        new EventSpec(VEHICLE_STATUS, EventType.NORMAL, "Cambio de estado", fields),
                        new EventSpec(TRIP_EVENT, EventType.NORMAL, "Viaje completado", fields),
                        new EventSpec(MAINTENANCE_ALERT, EventType.NORMAL, "Aviso de mantenimiento", fields),
                        new EventSpec(ERR_ENGINE_OVERHEAT, EventType.ERROR,
                                "Temperatura de motor sobre el umbral", fields),
                        new EventSpec(ERR_BRAKE_FAILURE, EventType.ERROR, "Fallo de frenos", fields),
                        new EventSpec(ERR_BATTERY_CRITICAL, EventType.ERROR, "Bateria en nivel critico", fields),
                        new EventSpec(ERR_GPS_LOSS, EventType.ERROR, "Perdida de senal GPS", fields)
                ),
                new ErrorSpec(ERROR_TYPES, 0.1,
                        List.of("engineTemp", "batteryLevel", "gpsLocation", "speed"))
        );
    }

    @Override
    public Event generateEvent(String eventType) {
        return switch (eventType) {
            case VEHICLE_TELEMETRY -> vehicleTelemetry();
            case VEHICLE_STATUS -> vehicleStatus();
            case TRIP_EVENT -> tripEvent();
            case MAINTENANCE_ALERT -> maintenanceAlert();
            case ERR_ENGINE_OVERHEAT, ERR_BRAKE_FAILURE, ERR_BATTERY_CRITICAL,
                 ERR_GPS_LOSS -> generateErrorEvent(eventType);
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + eventType);
        };
    }

    @Override
    public Event generateErrorEvent() {
        return generateErrorEvent(RandomUtils.randomFrom(ERROR_TYPES));
    }

    private Event generateErrorEvent(String errorType) {
        return switch (errorType) {
            case ERR_ENGINE_OVERHEAT -> buildErrorEvent(DOMAIN, ERR_ENGINE_OVERHEAT,
                    "Temperatura del motor por encima del umbral",
                    Severity.HIGH, overheatPayload());
            case ERR_BRAKE_FAILURE -> buildErrorEvent(DOMAIN, ERR_BRAKE_FAILURE,
                    "Fallo detectado en el sistema de frenos",
                    Severity.CRITICAL, brakeFailurePayload());
            case ERR_BATTERY_CRITICAL -> buildErrorEvent(DOMAIN, ERR_BATTERY_CRITICAL,
                    "Nivel de bateria critico",
                    Severity.HIGH, batteryCriticalPayload());
            case ERR_GPS_LOSS -> buildErrorEvent(DOMAIN, ERR_GPS_LOSS,
                    "Senal GPS perdida",
                    Severity.MEDIUM, gpsLossPayload());
            default -> throw new IllegalArgumentException("Tipo de error no soportado: " + errorType);
        };
    }

    // --- eventos normales ----------------------------------------------------

    private Event vehicleTelemetry() {
        double speed = round(RandomUtils.nextDouble(0.0, 140.0));
        Map<String, Object> payload = basePayload();
        payload.put("speed", speed);
        // en parado el motor no gira; en marcha las vueltas acompanan a la velocidad
        payload.put("rpm", speed < 1 ? 0 : (int) (700 + speed * RandomUtils.nextDouble(20, 40)));
        payload.put("fuelLevel", round(RandomUtils.nextDouble(5.0, 100.0)));
        payload.put("engineTemp", round(RandomUtils.nextDouble(70.0, ENGINE_TEMP_THRESHOLD_C - 1)));
        payload.put("gpsLocation", location());
        payload.put("odometer", round(RandomUtils.nextDouble(100.0, 300000.0)));
        return buildNormalEvent(DOMAIN, VEHICLE_TELEMETRY, payload);
    }

    private Event vehicleStatus() {
        String status = RandomUtils.randomFrom(STATUSES);
        Map<String, Object> payload = basePayload();
        payload.put("status", status);
        payload.put("batteryLevel", round(RandomUtils.nextDouble(
                BATTERY_CRITICAL_THRESHOLD + 0.1, 100.0)));
        payload.put("tirePressure", tirePressure());
        payload.put("speed", "MOVING".equals(status) ? round(RandomUtils.nextDouble(5.0, 130.0)) : 0.0);
        return buildNormalEvent(DOMAIN, VEHICLE_STATUS, payload);
    }

    private Event tripEvent() {
        double distance = round(RandomUtils.nextDouble(0.5, 850.0));
        Map<String, Object> payload = basePayload();
        payload.put("tripId", "TRIP-" + RandomUtils.nextInt(10000000, 99999999));
        payload.put("startLocation", location());
        payload.put("endLocation", location());
        payload.put("distanceKm", distance);
        // duracion coherente con la distancia, a una media de entre 25 y 95 km/h
        payload.put("durationMinutes", (int) Math.max(1,
                Math.round(distance / RandomUtils.nextDouble(25.0, 95.0) * 60)));
        payload.put("averageConsumption", round(RandomUtils.nextDouble(3.5, 12.0)));
        return buildNormalEvent(DOMAIN, TRIP_EVENT, payload);
    }

    private Event maintenanceAlert() {
        Map<String, Object> payload = basePayload();
        payload.put("component", RandomUtils.randomFrom(COMPONENTS));
        payload.put("alertType", RandomUtils.randomFrom(ALERT_TYPES));
        payload.put("odometer", round(RandomUtils.nextDouble(5000.0, 300000.0)));
        payload.put("dueInKm", RandomUtils.nextInt(0, 5000));
        return buildNormalEvent(DOMAIN, MAINTENANCE_ALERT, payload);
    }

    // --- payloads de error ---------------------------------------------------

    private Map<String, Object> overheatPayload() {
        Map<String, Object> payload = basePayload();
        // siempre por encima del umbral: eso es lo que lo convierte en incidente
        payload.put("engineTemp", round(RandomUtils.nextDouble(ENGINE_TEMP_THRESHOLD_C + 0.1, 145.0)));
        payload.put("threshold", ENGINE_TEMP_THRESHOLD_C);
        payload.put("coolantLevel", round(RandomUtils.nextDouble(0.0, 40.0)));
        payload.put("speed", round(RandomUtils.nextDouble(0.0, 120.0)));
        payload.put("gpsLocation", location());
        return payload;
    }

    private Map<String, Object> brakeFailurePayload() {
        Map<String, Object> payload = basePayload();
        payload.put("brakeType", RandomUtils.randomFrom("FRONT_LEFT", "FRONT_RIGHT",
                "REAR_LEFT", "REAR_RIGHT", "HANDBRAKE"));
        payload.put("padWearPercent", round(RandomUtils.nextDouble(85.0, 100.0)));
        payload.put("speed", round(RandomUtils.nextDouble(30.0, 130.0)));
        payload.put("gpsLocation", location());
        payload.put("immobilizeRequired", true);
        return payload;
    }

    private Map<String, Object> batteryCriticalPayload() {
        Map<String, Object> payload = basePayload();
        payload.put("batteryLevel", round(RandomUtils.nextDouble(0.0, BATTERY_CRITICAL_THRESHOLD - 0.1)));
        payload.put("threshold", BATTERY_CRITICAL_THRESHOLD);
        payload.put("status", "CHARGING");
        payload.put("estimatedRangeKm", round(RandomUtils.nextDouble(0.0, 25.0)));
        return payload;
    }

    private Map<String, Object> gpsLossPayload() {
        Map<String, Object> payload = basePayload();
        payload.put("lastKnownLocation", location());
        payload.put("gpsLocation", null);
        payload.put("minutesSinceLastFix", RandomUtils.nextInt(1, 240));
        payload.put("satellitesInView", RandomUtils.nextInt(0, 3));
        return payload;
    }

    // --- helpers -------------------------------------------------------------

    private Map<String, Object> basePayload() {
        return payload(
                "vehicleId", "VEH-" + RandomUtils.nextInt(100000, 999999),
                "plate", faker.vehicle().licensePlate(),
                "model", faker.vehicle().makeAndModel());
    }

    private Map<String, Object> location() {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("latitude", round(RandomUtils.nextDouble(-90.0, 90.0), 6));
        location.put("longitude", round(RandomUtils.nextDouble(-180.0, 180.0), 6));
        location.put("altitude", round(RandomUtils.nextDouble(0.0, 2500.0)));
        return location;
    }

    private Map<String, Object> tirePressure() {
        Map<String, Object> pressure = new LinkedHashMap<>();
        for (String wheel : List.of("frontLeft", "frontRight", "rearLeft", "rearRight")) {
            pressure.put(wheel, round(RandomUtils.nextDouble(1.8, 2.8)));
        }
        return pressure;
    }

    private double round(double value) {
        return round(value, 2);
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
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
