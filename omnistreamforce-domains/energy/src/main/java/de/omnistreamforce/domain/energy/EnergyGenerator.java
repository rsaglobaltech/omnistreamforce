package de.omnistreamforce.domain.energy;

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
 * Dominio de energia: generacion electrica, estado de la red, lecturas de contador y precios
 * de mercado.
 */
public class EnergyGenerator extends AbstractDomainGenerator {

    public static final String DOMAIN = "energy";

    public static final String POWER_GENERATION = "PowerGeneration";
    public static final String GRID_STATUS = "GridStatus";
    public static final String METER_READING = "MeterReading";
    public static final String PRICE_UPDATE = "PriceUpdate";

    public static final String ERR_POWER_OUTAGE = "PowerOutage";
    public static final String ERR_GRID_FREQUENCY_DEVIATION = "GridFrequencyDeviation";
    public static final String ERR_EQUIPMENT_FAILURE = "EquipmentFailure";

    /** Frecuencia nominal de la red europea. */
    public static final double NOMINAL_FREQUENCY_HZ = 50.0;
    /** Desviacion a partir de la cual se considera incidente. */
    public static final double FREQUENCY_THRESHOLD_HZ = 0.2;

    private static final List<String> NORMAL_TYPES =
            List.of(POWER_GENERATION, GRID_STATUS, METER_READING, PRICE_UPDATE);
    private static final List<String> ERROR_TYPES =
            List.of(ERR_POWER_OUTAGE, ERR_GRID_FREQUENCY_DEVIATION, ERR_EQUIPMENT_FAILURE);

    private static final List<String> SOURCES = List.of("SOLAR", "WIND", "HYDRO", "NUCLEAR", "GAS");
    private static final List<String> STABILITY = List.of("STABLE", "DEGRADED", "CRITICAL");
    private static final List<String> MARKETS = List.of("SPOT", "INTRADAY", "DAY_AHEAD", "FUTURES");
    private static final List<String> CURRENCIES = List.of("EUR", "USD", "GBP");
    private static final List<String> EQUIPMENT = List.of("TURBINE", "TRANSFORMER", "INVERTER",
            "COOLING_SYSTEM", "GENERATOR");
    private static final List<String> OUTAGE_CAUSES = List.of("STORM", "OVERLOAD", "MAINTENANCE_FAILURE",
            "CYBER_INCIDENT", "WILDFIRE");

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
                fd("plantId", "string", true, "regex PLANT-[0-9]{4}", "Planta generadora"),
                fd("source", "enum", false, null, SOURCES, "Fuente de energia"),
                fd("outputMW", "double", false, "number.numberBetween(0.0,900.0)", "Potencia entregada en MW"),
                fd("efficiency", "double", false, "number.numberBetween(0.5,0.99)", "Rendimiento de la planta"),
                fd("gridId", "string", false, "regex GRID-[0-9]{3}", "Segmento de red"),
                fd("frequency", "double", false, null, "Frecuencia de la red en Hz"),
                fd("voltage", "double", false, null, "Tension en kV"),
                fd("load", "double", false, null, "Carga de la red en porcentaje"),
                fd("stability", "enum", false, null, STABILITY, "Estabilidad de la red"),
                fd("meterId", "string", false, "regex MTR-[0-9]{8}", "Contador"),
                fd("customerId", "string", false, "regex CUST-[0-9]{6}", "Cliente"),
                fd("consumptionKWh", "double", false, null, "Consumo del periodo en kWh"),
                fd("market", "enum", false, null, MARKETS, "Mercado electrico"),
                fd("pricePerMWh", "double", false, null, "Precio por MWh"),
                fd("currency", "enum", false, null, CURRENCIES, "Moneda"),
                fd("period", "string", false, null, "Periodo de la cotizacion")
        );

        return new EventSchema(
                DOMAIN,
                "EnergyEvents",
                "Eventos del dominio energetico: generacion, estado de red, consumo y precios.",
                fields,
                List.of(
                        new EventSpec(POWER_GENERATION, EventType.NORMAL, "Produccion de una planta", fields),
                        new EventSpec(GRID_STATUS, EventType.NORMAL, "Estado de un segmento de red", fields),
                        new EventSpec(METER_READING, EventType.NORMAL, "Lectura de contador", fields),
                        new EventSpec(PRICE_UPDATE, EventType.NORMAL, "Actualizacion de precio", fields),
                        new EventSpec(ERR_POWER_OUTAGE, EventType.ERROR, "Corte de suministro", fields),
                        new EventSpec(ERR_GRID_FREQUENCY_DEVIATION, EventType.ERROR,
                                "Desviacion de frecuencia sobre el umbral", fields),
                        new EventSpec(ERR_EQUIPMENT_FAILURE, EventType.ERROR, "Fallo de equipo", fields)
                ),
                new ErrorSpec(ERROR_TYPES, 0.08,
                        List.of("frequency", "outputMW", "stability", "efficiency"))
        );
    }

    @Override
    public Event generateEvent(String eventType) {
        return switch (eventType) {
            case POWER_GENERATION -> powerGeneration();
            case GRID_STATUS -> gridStatus();
            case METER_READING -> meterReading();
            case PRICE_UPDATE -> priceUpdate();
            case ERR_POWER_OUTAGE, ERR_GRID_FREQUENCY_DEVIATION,
                 ERR_EQUIPMENT_FAILURE -> generateErrorEvent(eventType);
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + eventType);
        };
    }

    @Override
    public Event generateErrorEvent() {
        return generateErrorEvent(RandomUtils.randomFrom(ERROR_TYPES));
    }

    private Event generateErrorEvent(String errorType) {
        return switch (errorType) {
            case ERR_POWER_OUTAGE -> buildErrorEvent(DOMAIN, ERR_POWER_OUTAGE,
                    "Corte de suministro en la planta",
                    Severity.CRITICAL, outagePayload());
            case ERR_GRID_FREQUENCY_DEVIATION -> buildErrorEvent(DOMAIN, ERR_GRID_FREQUENCY_DEVIATION,
                    "Frecuencia de red fuera del margen admitido",
                    Severity.HIGH, frequencyDeviationPayload());
            case ERR_EQUIPMENT_FAILURE -> buildErrorEvent(DOMAIN, ERR_EQUIPMENT_FAILURE,
                    "Fallo de equipo en planta",
                    Severity.HIGH, equipmentFailurePayload());
            default -> throw new IllegalArgumentException("Tipo de error no soportado: " + errorType);
        };
    }

    // --- eventos normales ----------------------------------------------------

    private Event powerGeneration() {
        String source = RandomUtils.randomFrom(SOURCES);
        Map<String, Object> payload = payload(
                "plantId", plantId(),
                "source", source,
                "outputMW", round(outputFor(source)),
                "efficiency", round(efficiencyFor(source)),
                "capacityFactor", round(RandomUtils.nextDouble(0.2, 0.95)),
                "co2AvoidedTons", round(RandomUtils.nextDouble(0.0, 500.0)));
        return buildNormalEvent(DOMAIN, POWER_GENERATION, payload);
    }

    private Event gridStatus() {
        Map<String, Object> payload = payload(
                "gridId", gridId(),
                // dentro del margen: los desvios son evento de error
                "frequency", round(RandomUtils.nextDouble(
                        NOMINAL_FREQUENCY_HZ - FREQUENCY_THRESHOLD_HZ + 0.01,
                        NOMINAL_FREQUENCY_HZ + FREQUENCY_THRESHOLD_HZ - 0.01)),
                "voltage", round(RandomUtils.nextDouble(215.0, 245.0)),
                "load", round(RandomUtils.nextDouble(20.0, 95.0)),
                "stability", RandomUtils.chance(0.85) ? "STABLE" : "DEGRADED",
                "reserveMarginPercent", round(RandomUtils.nextDouble(5.0, 30.0)));
        return buildNormalEvent(DOMAIN, GRID_STATUS, payload);
    }

    private Event meterReading() {
        Map<String, Object> payload = payload(
                "meterId", "MTR-" + RandomUtils.nextInt(10000000, 99999999),
                "customerId", "CUST-" + RandomUtils.nextInt(100000, 999999),
                "consumptionKWh", round(RandomUtils.nextDouble(0.1, 45.0)),
                "tariff", RandomUtils.randomFrom("PEAK", "OFF_PEAK", "FLAT"),
                "readingType", RandomUtils.randomFrom("AUTOMATIC", "MANUAL", "ESTIMATED"),
                "address", faker.address().fullAddress());
        return buildNormalEvent(DOMAIN, METER_READING, payload);
    }

    private Event priceUpdate() {
        Map<String, Object> payload = payload(
                "market", RandomUtils.randomFrom(MARKETS),
                "pricePerMWh", round(RandomUtils.nextDouble(-20.0, 350.0)),
                "currency", RandomUtils.randomFrom(CURRENCIES),
                "period", "H" + RandomUtils.nextInt(1, 24),
                "volumeMWh", round(RandomUtils.nextDouble(10.0, 5000.0)));
        return buildNormalEvent(DOMAIN, PRICE_UPDATE, payload);
    }

    // --- payloads de error ---------------------------------------------------

    private Map<String, Object> outagePayload() {
        int areas = RandomUtils.nextInt(1, 5);
        List<String> affected = new java.util.ArrayList<>();
        for (int i = 0; i < areas; i++) {
            affected.add(faker.address().city());
        }
        return payload(
                "plantId", plantId(),
                "gridId", gridId(),
                "affectedAreas", affected,
                "affectedCustomers", RandomUtils.nextInt(500, 250000),
                "cause", RandomUtils.randomFrom(OUTAGE_CAUSES),
                "outputMW", 0.0,
                "estimatedRestoreMinutes", RandomUtils.nextInt(15, 720));
    }

    private Map<String, Object> frequencyDeviationPayload() {
        // el desvio siempre supera el umbral: es lo que hace que sea un incidente
        double deviation = RandomUtils.nextDouble(FREQUENCY_THRESHOLD_HZ + 0.01, 1.5);
        boolean above = RandomUtils.nextBoolean();
        return payload(
                "gridId", gridId(),
                "frequency", round(NOMINAL_FREQUENCY_HZ + (above ? deviation : -deviation)),
                "nominalFrequency", NOMINAL_FREQUENCY_HZ,
                "deviation", round(deviation),
                "threshold", FREQUENCY_THRESHOLD_HZ,
                "stability", "CRITICAL",
                "load", round(RandomUtils.nextDouble(90.0, 130.0)));
    }

    private Map<String, Object> equipmentFailurePayload() {
        return payload(
                "plantId", plantId(),
                "equipmentType", RandomUtils.randomFrom(EQUIPMENT),
                "failureType", RandomUtils.randomFrom("OVERHEAT", "VIBRATION", "SHORT_CIRCUIT",
                        "LUBRICATION_LOSS", "SENSOR_FAULT"),
                "outputMW", round(RandomUtils.nextDouble(0.0, 50.0)),
                "efficiency", round(RandomUtils.nextDouble(0.05, 0.4)),
                "maintenanceRequired", true);
    }

    // --- helpers -------------------------------------------------------------

    /** Cada fuente tiene su rango tipico de potencia. */
    private double outputFor(String source) {
        return switch (source) {
            case "NUCLEAR" -> RandomUtils.nextDouble(400.0, 900.0);
            case "GAS" -> RandomUtils.nextDouble(100.0, 500.0);
            case "HYDRO" -> RandomUtils.nextDouble(50.0, 400.0);
            case "WIND" -> RandomUtils.nextDouble(0.0, 250.0);
            default -> RandomUtils.nextDouble(0.0, 180.0);
        };
    }

    private double efficiencyFor(String source) {
        return switch (source) {
            case "NUCLEAR" -> RandomUtils.nextDouble(0.85, 0.95);
            case "HYDRO" -> RandomUtils.nextDouble(0.80, 0.92);
            case "GAS" -> RandomUtils.nextDouble(0.45, 0.62);
            case "WIND" -> RandomUtils.nextDouble(0.30, 0.50);
            default -> RandomUtils.nextDouble(0.15, 0.25);
        };
    }

    private String plantId() {
        return "PLANT-" + RandomUtils.nextInt(1000, 9999);
    }

    private String gridId() {
        return "GRID-" + RandomUtils.nextInt(100, 999);
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
