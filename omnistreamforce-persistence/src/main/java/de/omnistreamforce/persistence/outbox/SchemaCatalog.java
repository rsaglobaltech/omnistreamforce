package de.omnistreamforce.persistence.outbox;

import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.ErrorSpec;
import de.omnistreamforce.domain.DomainGenerator;
import de.omnistreamforce.domain.DomainRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Resuelve el {@link EventSchema} de un dominio para saber que columnas tiene su tabla.
 * <p>
 * Si el dominio no tiene esquema conocido (por ejemplo uno generado en caliente) no se falla:
 * se usa un esquema vacio, con lo que la tabla tendra solo envelope y JSON. Sin columnas tipadas,
 * pero sin perder un solo dato.
 */
public final class SchemaCatalog implements Function<String, EventSchema> {

    private final Function<String, EventSchema> delegate;
    private final Map<String, EventSchema> cache = new ConcurrentHashMap<>();

    public SchemaCatalog(Function<String, EventSchema> delegate) {
        this.delegate = delegate;
    }

    public static SchemaCatalog fromRegistry(DomainRegistry registry) {
        return new SchemaCatalog(domain -> registry.get(domain)
                .map(DomainGenerator::getSchema)
                .orElse(null));
    }

    public static SchemaCatalog of(EventSchema... schemas) {
        Map<String, EventSchema> byDomain = new ConcurrentHashMap<>();
        for (EventSchema schema : schemas) {
            byDomain.put(schema.domain(), schema);
        }
        return new SchemaCatalog(byDomain::get);
    }

    @Override
    public EventSchema apply(String domain) {
        return cache.computeIfAbsent(domain, d -> {
            EventSchema schema = delegate == null ? null : delegate.apply(d);
            return schema != null ? schema : empty(d);
        });
    }

    private static EventSchema empty(String domain) {
        return new EventSchema(domain, domain + "-events",
                "Esquema desconocido: la tabla solo tendra envelope y payload JSON",
                List.of(), List.of(), new ErrorSpec(List.of(), 0.0, List.of()));
    }
}
