package de.omnistreamforce.ai.domain;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.EventSpec;
import de.omnistreamforce.core.EventType;
import de.omnistreamforce.core.FieldDefinition;
import de.omnistreamforce.core.Severity;
import de.omnistreamforce.domain.AbstractDomainGenerator;
import de.omnistreamforce.util.RandomUtils;
import net.datafaker.Faker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte cualquier {@link EventSchema} en un generador utilizable por el motor.
 * <p>
 * Es lo que hace aprovechable un esquema propuesto por un modelo: en cuanto se tiene el esquema,
 * el dominio se puede registrar y publicar como los que vienen escritos a mano. Tambien sirve
 * para esquemas escritos a mano en YAML, sin IA de por medio.
 * <p>
 * Los valores salen de DataFaker guiados por el nombre y el tipo de cada campo; los eventos de
 * error se construyen degradando el payload de una de las formas tipicas (campo ausente, valor
 * fuera de rango, formato invalido o nulo indebido).
 */
public class SchemaBackedGenerator extends AbstractDomainGenerator {

    /** Como se estropea el payload en un evento de error. */
    private enum Corruption {
        MISSING_FIELD, OUT_OF_RANGE, INVALID_FORMAT, NULL_VALUE
    }

    private final EventSchema schema;
    private final List<String> normalTypes;
    private final List<String> errorTypes;
    private final Faker faker = new Faker();

    public SchemaBackedGenerator(EventSchema schema) {
        this.schema = schema;
        this.normalTypes = schema.eventTypes().stream()
                .filter(spec -> spec.eventType() != EventType.ERROR)
                .map(EventSpec::typeName)
                .toList();
        this.errorTypes = schema.eventTypes().stream()
                .filter(spec -> spec.eventType() == EventType.ERROR)
                .map(EventSpec::typeName)
                .toList();
        if (normalTypes.isEmpty()) {
            throw new IllegalArgumentException("El esquema no declara eventos normales: " + schema.domain());
        }
    }

    @Override
    public String getDomainName() {
        return schema.domain();
    }

    @Override
    public List<String> getSupportedEventTypes() {
        return normalTypes;
    }

    @Override
    public List<String> getErrorTypes() {
        return errorTypes;
    }

    @Override
    public EventSchema getSchema() {
        return schema;
    }

    @Override
    public Event generateEvent(String eventType) {
        if (normalTypes.contains(eventType)) {
            return buildNormalEvent(schema.domain(), eventType, payloadFor(eventType));
        }
        if (errorTypes.contains(eventType)) {
            return errorEvent(eventType);
        }
        throw new IllegalArgumentException("Tipo de evento no soportado: " + eventType);
    }

    @Override
    public Event generateErrorEvent() {
        if (errorTypes.isEmpty()) {
            throw new IllegalStateException("El esquema no declara tipos de error: " + schema.domain());
        }
        return errorEvent(RandomUtils.randomFrom(errorTypes));
    }

    private Event errorEvent(String errorType) {
        Corruption corruption = RandomUtils.randomFrom(Corruption.values());
        Map<String, Object> payload = corrupt(payloadFor(errorType), corruption);
        return buildErrorEvent(schema.domain(), errorType,
                describe(corruption), severityFor(corruption), payload);
    }

    // --- construccion del payload --------------------------------------------

    private Map<String, Object> payloadFor(String eventType) {
        List<FieldDefinition> fields = fieldsFor(eventType);
        Map<String, Object> payload = new LinkedHashMap<>();
        for (FieldDefinition field : fields) {
            payload.put(field.name(), valueFor(field));
        }
        return payload;
    }

    /** Los campos propios del tipo de evento si los declara; si no, los comunes del esquema. */
    private List<FieldDefinition> fieldsFor(String eventType) {
        return schema.eventTypes().stream()
                .filter(spec -> spec.typeName().equals(eventType))
                .map(EventSpec::fields)
                .filter(list -> list != null && !list.isEmpty())
                .findFirst()
                .orElse(schema.fields());
    }

    /**
     * El nombre del campo da mas pistas que el tipo: un campo llamado "email" debe parecer un
     * email aunque el esquema solo diga "string".
     */
    private Object valueFor(FieldDefinition field) {
        if (field.enumValues() != null && !field.enumValues().isEmpty()) {
            return RandomUtils.randomFrom(field.enumValues());
        }
        String name = field.name().toLowerCase(Locale.ROOT);
        String type = field.type() == null ? "string" : field.type().toLowerCase(Locale.ROOT);

        return switch (type) {
            case "int" -> intFor(name);
            case "double" -> doubleFor(name);
            case "boolean" -> RandomUtils.nextBoolean();
            case "datetime" -> Instant.now().minusSeconds(RandomUtils.nextInt(0, 86_400)).toString();
            case "array" -> arrayFor(name);
            case "object" -> objectFor(name);
            default -> stringFor(name);
        };
    }

    private Object stringFor(String name) {
        if (name.contains("email")) {
            return faker.internet().emailAddress();
        }
        if (name.contains("phone")) {
            return faker.phoneNumber().phoneNumber();
        }
        if (name.contains("city")) {
            return faker.address().city();
        }
        if (name.contains("country")) {
            return faker.address().countryCode();
        }
        if (name.contains("address")) {
            return faker.address().fullAddress();
        }
        if (name.contains("name")) {
            return faker.name().fullName();
        }
        if (name.contains("url")) {
            return faker.internet().url();
        }
        if (name.contains("plate")) {
            return faker.vehicle().licensePlate();
        }
        if (name.contains("currency")) {
            return RandomUtils.randomFrom("EUR", "USD", "GBP");
        }
        if (name.contains("status") || name.contains("state")) {
            return RandomUtils.randomFrom("NEW", "IN_PROGRESS", "DONE", "CANCELLED");
        }
        if (name.endsWith("id") || name.contains("uuid") || name.contains("reference")) {
            return prefixFor(name) + "-" + RandomUtils.nextInt(100000, 999999);
        }
        return faker.lorem().word();
    }

    private long intFor(String name) {
        if (name.contains("age")) {
            return RandomUtils.nextInt(0, 100);
        }
        if (name.contains("count") || name.contains("quantity") || name.contains("units")) {
            return RandomUtils.nextInt(1, 500);
        }
        if (name.contains("year")) {
            return RandomUtils.nextInt(1990, 2030);
        }
        return RandomUtils.nextInt(1, 10_000);
    }

    private double doubleFor(String name) {
        double value;
        if (name.contains("percent") || name.contains("rate") || name.contains("ratio")) {
            value = RandomUtils.nextDouble(0.0, 100.0);
        } else if (name.contains("price") || name.contains("amount") || name.contains("total")) {
            value = RandomUtils.nextDouble(1.0, 5_000.0);
        } else if (name.contains("latitude")) {
            value = RandomUtils.nextDouble(-90.0, 90.0);
        } else if (name.contains("longitude")) {
            value = RandomUtils.nextDouble(-180.0, 180.0);
        } else if (name.contains("temp")) {
            value = RandomUtils.nextDouble(-10.0, 45.0);
        } else {
            value = RandomUtils.nextDouble(0.0, 1_000.0);
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private List<Map<String, Object>> arrayFor(String name) {
        List<Map<String, Object>> items = new ArrayList<>();
        int count = RandomUtils.nextInt(1, 4);
        for (int i = 0; i < count; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", prefixFor(name) + "-" + RandomUtils.nextInt(1000, 9999));
            item.put("name", faker.commerce().productName());
            item.put("quantity", RandomUtils.nextInt(1, 10));
            items.add(item);
        }
        return items;
    }

    private Map<String, Object> objectFor(String name) {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("id", prefixFor(name) + "-" + RandomUtils.nextInt(1000, 9999));
        nested.put("label", faker.lorem().word());
        nested.put("value", Math.round(RandomUtils.nextDouble(0.0, 100.0) * 100.0) / 100.0);
        return nested;
    }

    private String prefixFor(String name) {
        String prefix = name.replaceAll("[^a-z]", "");
        return prefix.substring(0, Math.min(3, prefix.length())).toUpperCase(Locale.ROOT);
    }

    // --- degradacion controlada ----------------------------------------------

    private Map<String, Object> corrupt(Map<String, Object> payload, Corruption corruption) {
        if (payload.isEmpty()) {
            return payload;
        }
        List<String> keys = new ArrayList<>(payload.keySet());
        String victim = RandomUtils.randomFrom(keys);

        switch (corruption) {
            case MISSING_FIELD -> payload.remove(victim);
            case NULL_VALUE -> payload.put(victim, null);
            case INVALID_FORMAT -> payload.put(victim, "!!INVALID!!");
            case OUT_OF_RANGE -> {
                Object value = payload.get(victim);
                if (value instanceof Number) {
                    payload.put(victim, Long.MAX_VALUE / 2);
                } else {
                    payload.put(victim, "X".repeat(600));
                }
            }
        }
        payload.put("corruptedField", victim);
        return payload;
    }

    private String describe(Corruption corruption) {
        return switch (corruption) {
            case MISSING_FIELD -> "Falta un campo requerido";
            case OUT_OF_RANGE -> "Valor fuera del rango admitido";
            case INVALID_FORMAT -> "Formato de valor invalido";
            case NULL_VALUE -> "Valor nulo en un campo que no lo admite";
        };
    }

    private Severity severityFor(Corruption corruption) {
        return switch (corruption) {
            case MISSING_FIELD, NULL_VALUE -> Severity.HIGH;
            case OUT_OF_RANGE -> Severity.MEDIUM;
            case INVALID_FORMAT -> Severity.LOW;
        };
    }
}
