package de.omnistreamforce.persistence.ddl;

import de.omnistreamforce.core.FieldDefinition;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Traduce el {@code type} declarado en un {@link FieldDefinition} a un {@link SqlType}.
 * <p>
 * Los dominios actuales solo usan seis tipos (string, enum, int, double, array, object), pero el
 * mapeo reconoce por adelantado los que apareceran cuando los esquemas los genere un LLM. Un tipo
 * desconocido nunca hace fallar la generacion: cae en JSON.
 */
public final class TypeMapper {

    /** Campos cuyo nombre indica importe monetario: merecen decimal exacto, no binario flotante. */
    private static final Pattern MONEY = Pattern.compile(
            "(?i).*(amount|price|total|cost|fee|balance|revenue|salary).*");

    private TypeMapper() {
    }

    public static SqlType map(FieldDefinition field, DdlOptions options) {
        String type = field.type() == null ? "" : field.type().trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "string", "enum", "text", "uuid" -> SqlType.TEXT;
            case "int", "integer", "long", "short" -> SqlType.BIGINT;
            case "double", "float", "number" -> numeric(field, options);
            case "decimal", "bigdecimal", "money" -> SqlType.NUMERIC;
            case "boolean", "bool" -> SqlType.BOOLEAN;
            case "datetime", "timestamp", "date", "time", "instant" -> SqlType.TIMESTAMPTZ;
            // array, object, map y cualquier tipo futuro desconocido
            default -> SqlType.JSON;
        };
    }

    private static SqlType numeric(FieldDefinition field, DdlOptions options) {
        if (options.moneyAsNumeric() && field.name() != null && MONEY.matcher(field.name()).matches()) {
            return SqlType.NUMERIC;
        }
        return SqlType.DOUBLE;
    }
}
