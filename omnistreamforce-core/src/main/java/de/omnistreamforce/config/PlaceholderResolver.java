package de.omnistreamforce.config;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sustituye referencias a variables de entorno dentro de los valores de la configuracion.
 * <p>
 * Formas admitidas, con valor por defecto opcional tras {@code :-}:
 * <pre>
 * ${KAFKA_BOOTSTRAP_SERVERS}
 * ${env:OPENAI_API_KEY}
 * ${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
 * </pre>
 * Asi las credenciales nunca tienen que escribirse en el fichero.
 */
public final class PlaceholderResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{(env:)?([A-Za-z0-9_.]+)(:-([^}]*))?}");

    private final Function<String, String> environment;

    public PlaceholderResolver() {
        this(System::getenv);
    }

    public PlaceholderResolver(Function<String, String> environment) {
        this.environment = environment;
    }

    /** Resuelve todos los valores de texto de una estructura cargada desde YAML. */
    @SuppressWarnings("unchecked")
    public Object resolve(Object value) {
        if (value instanceof String text) {
            return resolveText(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new java.util.LinkedHashMap<>();
            map.forEach((key, item) -> resolved.put(String.valueOf(key), resolve(item)));
            return resolved;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::resolve).toList();
        }
        return value;
    }

    /**
     * Sustituye los marcadores de un texto. Si la variable no existe y no hay valor por defecto,
     * el marcador se deja tal cual: es mas facil de diagnosticar que un valor vacio silencioso.
     */
    public String resolveText(String text) {
        if (text == null || text.indexOf("${") < 0) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(2);
            String fallback = matcher.group(4);
            String value = environment.apply(name);
            if (value == null || value.isEmpty()) {
                value = fallback != null ? fallback : matcher.group(0);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
