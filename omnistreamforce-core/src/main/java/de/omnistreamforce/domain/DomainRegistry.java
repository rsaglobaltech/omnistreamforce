package de.omnistreamforce.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registro y descubrimiento de dominios disponibles.
 * <p>
 * Utiliza {@link ServiceLoader} (Java SPI) para descubrir implementaciones de
 * {@link DomainGenerator} presentes en el classpath. Tambien permite registrar
 * dominios programaticamente (utiles para dominios generados por IA).
 */
public class DomainRegistry {

    private static final Logger log = LoggerFactory.getLogger(DomainRegistry.class);

    private final Map<String, DomainGenerator> generators = new LinkedHashMap<>();

    public DomainRegistry() {
        this(true);
    }

    public DomainRegistry(boolean autoDiscover) {
        if (autoDiscover) {
            discover();
        }
    }

    /**
     * Descubre dominios disponibles via ServiceLoader.
     */
    public void discover() {
        ServiceLoader<DomainGenerator> loader = ServiceLoader.load(DomainGenerator.class);
        for (DomainGenerator generator : loader) {
            register(generator);
        }
        log.debug("Descubrimiento de dominios completo: {} dominio(s) registrado(s)", generators.size());
    }

    public void register(DomainGenerator generator) {
        generators.put(generator.getDomainName(), generator);
        log.debug("Dominio registrado: {}", generator.getDomainName());
    }

    public boolean unregister(String domainName) {
        return generators.remove(domainName) != null;
    }

    public Optional<DomainGenerator> get(String domainName) {
        return Optional.ofNullable(generators.get(domainName));
    }

    public Collection<String> listDomainNames() {
        return Collections.unmodifiableSet(generators.keySet());
    }

    public Collection<DomainGenerator> listGenerators() {
        return Collections.unmodifiableCollection(generators.values());
    }

    public boolean isAvailable(String domainName) {
        return generators.containsKey(domainName);
    }

    public int size() {
        return generators.size();
    }
}