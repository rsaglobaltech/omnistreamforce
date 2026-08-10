package de.omnistreamforce.ai.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.omnistreamforce.core.ErrorSpec;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.EventSpec;
import de.omnistreamforce.core.EventType;
import de.omnistreamforce.core.FieldDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Convierte la respuesta de un modelo en un {@link EventSchema}.
 * <p>
 * Un modelo no siempre devuelve JSON limpio: lo envuelve en bloques markdown, lo precede de una
 * frase, escribe los tipos como le parece o se deja campos. El parser es deliberadamente
 * tolerante en la forma y estricto en el resultado: lo que sale siempre es un esquema utilizable.
 */
public class SchemaParser {

    /** Tipos que el resto del sistema sabe traducir a columnas y a datos falsos. */
    private static final Set<String> KNOWN_TYPES = Set.of(
            "string", "int", "double", "boolean", "enum", "array", "object", "datetime");

    private final ObjectMapper mapper = new ObjectMapper();

    public static class ParseException extends RuntimeException {
        public ParseException(String message) {
            super(message);
        }

        public ParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public EventSchema parse(String response, String fallbackDomain) {
        String json = extractJson(response);
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new ParseException("La respuesta del modelo no es JSON valido", e);
        }
        if (!root.isObject()) {
            throw new ParseException("Se esperaba un objeto JSON con el esquema");
        }

        String domain = text(root, "domain", fallbackDomain);
        if (domain == null || domain.isBlank()) {
            throw new ParseException("El esquema no indica dominio");
        }
        String name = text(root, "name", capitalize(domain) + "Events");
        String description = text(root, "description", "Esquema propuesto para " + domain);

        List<FieldDefinition> fields = parseFields(root.path("fields"));
        if (fields.isEmpty()) {
            throw new ParseException("El esquema no declara ningun campo");
        }

        List<String> normalEvents = parseEventNames(root, "normalEvents", "events");
        List<String> errorEvents = parseEventNames(root, "errorEvents", "errors");
        if (normalEvents.isEmpty()) {
            throw new ParseException("El esquema no declara eventos normales");
        }

        List<EventSpec> specs = new ArrayList<>();
        normalEvents.forEach(event ->
                specs.add(new EventSpec(event, EventType.NORMAL, "Evento de " + domain, fields)));
        errorEvents.forEach(event ->
                specs.add(new EventSpec(event, EventType.ERROR, "Error de " + domain, fields)));

        double errorRate = root.path("errorRate").isNumber() ? root.path("errorRate").asDouble() : 0.1;
        if (errorRate < 0 || errorRate > 1) {
            // algunos modelos responden en porcentaje
            errorRate = errorRate > 1 && errorRate <= 100 ? errorRate / 100.0 : 0.1;
        }

        return new EventSchema(domain, name, description, fields, specs,
                new ErrorSpec(errorEvents, errorRate,
                        fields.stream().limit(3).map(FieldDefinition::name).toList()));
    }

    /**
     * Recorta lo que rodea al JSON: bloques markdown o una frase introductoria.
     */
    String extractJson(String response) {
        if (response == null || response.isBlank()) {
            throw new ParseException("El modelo no devolvio nada");
        }
        String text = response.trim();

        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = text.indexOf("```", fence + 3);
            if (start > 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }

        int first = text.indexOf('{');
        int last = text.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new ParseException("No se encontro un objeto JSON en la respuesta");
        }
        return text.substring(first, last + 1);
    }

    private List<FieldDefinition> parseFields(JsonNode node) {
        List<FieldDefinition> fields = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (!node.isArray()) {
            return fields;
        }
        for (JsonNode item : node) {
            String name = text(item, "name", null);
            if (name == null || name.isBlank() || !seen.add(name)) {
                continue;   // sin nombre o repetido: no aporta nada
            }
            List<String> enumValues = new ArrayList<>();
            JsonNode values = item.path("enumValues");
            if (values.isArray()) {
                values.forEach(value -> enumValues.add(value.asText()));
            }
            String type = normalizeType(text(item, "type", "string"), !enumValues.isEmpty());
            fields.add(new FieldDefinition(
                    name,
                    type,
                    item.path("required").asBoolean(false),
                    text(item, "fakerExpression", null),
                    null,
                    enumValues,
                    text(item, "description", null)));
        }
        return fields;
    }

    /**
     * Normaliza lo que escriba el modelo a los tipos que el resto del sistema entiende.
     */
    String normalizeType(String raw, boolean hasEnumValues) {
        if (hasEnumValues) {
            return "enum";
        }
        String type = raw == null ? "string" : raw.trim().toLowerCase(Locale.ROOT);
        String normalized = switch (type) {
            case "str", "text", "varchar", "uuid", "String" -> "string";
            case "integer", "long", "number", "bigint", "short" -> "int";
            case "float", "decimal", "bigdecimal", "money" -> "double";
            case "bool" -> "boolean";
            case "list", "array[]", "collection" -> "array";
            case "map", "nested", "struct", "json" -> "object";
            case "date", "timestamp", "time", "instant" -> "datetime";
            default -> type;
        };
        return KNOWN_TYPES.contains(normalized) ? normalized : "string";
    }

    private List<String> parseEventNames(JsonNode root, String primary, String alternative) {
        JsonNode node = root.has(primary) ? root.path(primary) : root.path(alternative);
        List<String> names = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (!node.isArray()) {
            return names;
        }
        for (JsonNode item : node) {
            // el modelo puede devolver strings sueltos u objetos con nombre
            String name = item.isTextual() ? item.asText() : text(item, "name", null);
            if (name != null && !name.isBlank() && seen.add(name)) {
                names.add(name.trim());
            }
        }
        return names;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? fallback : value.asText().trim();
    }

    private String capitalize(String value) {
        return value.isEmpty() ? value
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
