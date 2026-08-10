package de.omnistreamforce.ai;

import de.omnistreamforce.ai.domain.SchemaBackedGenerator;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.domain.DomainGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Propone esquemas para dominios que no existen en el classpath y los convierte en generadores
 * listos para publicar.
 * <p>
 * Cachea por dominio: proponer cuesta dinero y tiempo, y el esquema no cambia entre llamadas.
 */
public class SchemaProposer {

    private static final Logger log = LoggerFactory.getLogger(SchemaProposer.class);

    /** Minimos para que un esquema sea aprovechable. */
    private static final int MIN_FIELDS = 2;
    private static final int MIN_NORMAL_EVENTS = 1;

    private final AIService aiService;
    private final Map<String, EventSchema> cache = new ConcurrentHashMap<>();

    public SchemaProposer(AIService aiService) {
        this.aiService = aiService;
    }

    public boolean isAvailable() {
        return aiService.isAvailable();
    }

    /**
     * Propone el esquema del dominio, o devuelve el ya propuesto si se pidio antes.
     *
     * @throws AIUnavailableException si el modelo no responde
     * @throws IllegalStateException  si responde algo inservible
     */
    public EventSchema propose(String domainName, String description) {
        EventSchema cached = cache.get(domainName);
        if (cached != null) {
            log.debug("Esquema de '{}' servido desde la cache", domainName);
            return cached;
        }
        EventSchema schema = aiService.proposeSchema(domainName, description);
        List<String> problems = validate(schema);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("El esquema propuesto para '" + domainName
                    + "' no es utilizable: " + String.join("; ", problems));
        }
        cache.put(domainName, schema);
        log.info("Esquema propuesto para '{}': {} campos, {} tipos de evento",
                domainName, schema.fields().size(), schema.eventTypes().size());
        return schema;
    }

    /**
     * Como {@link #propose}, pero sin excepciones: vacio si la IA no esta o no sirve. Pensado
     * para flujos donde la IA es opcional.
     */
    public Optional<EventSchema> tryPropose(String domainName, String description) {
        try {
            return Optional.of(propose(domainName, description));
        } catch (RuntimeException e) {
            log.warn("No se pudo proponer un esquema para '{}': {}", domainName, e.getMessage());
            return Optional.empty();
        }
    }

    /** El esquema propuesto, ya envuelto en un generador que el motor puede usar. */
    public DomainGenerator proposeGenerator(String domainName, String description) {
        return new SchemaBackedGenerator(propose(domainName, description));
    }

    /** Registra un esquema hecho a mano, para no llamar al modelo. */
    public void register(EventSchema schema) {
        List<String> problems = validate(schema);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Esquema invalido: " + String.join("; ", problems));
        }
        cache.put(schema.domain(), schema);
    }

    public Optional<EventSchema> cached(String domainName) {
        return Optional.ofNullable(cache.get(domainName));
    }

    public void clearCache() {
        cache.clear();
    }

    /** Comprueba los minimos; devuelve la lista de problemas, vacia si el esquema sirve. */
    public List<String> validate(EventSchema schema) {
        List<String> problems = new ArrayList<>();
        if (schema == null) {
            return List.of("no hay esquema");
        }
        if (schema.domain() == null || schema.domain().isBlank()) {
            problems.add("sin nombre de dominio");
        }
        if (schema.fields() == null || schema.fields().size() < MIN_FIELDS) {
            problems.add("hacen falta al menos " + MIN_FIELDS + " campos");
        }
        long normalEvents = schema.eventTypes() == null ? 0 : schema.eventTypes().stream()
                .filter(spec -> spec.eventType() != de.omnistreamforce.core.EventType.ERROR)
                .count();
        if (normalEvents < MIN_NORMAL_EVENTS) {
            problems.add("hace falta al menos " + MIN_NORMAL_EVENTS + " evento normal");
        }
        if (schema.fields() != null && schema.fields().stream()
                .anyMatch(field -> field.name() == null || field.name().isBlank())) {
            problems.add("hay campos sin nombre");
        }
        return problems;
    }
}
