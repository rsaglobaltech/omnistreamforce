package de.omnistreamforce.engine;

import de.omnistreamforce.domain.DomainGenerator;
import de.omnistreamforce.routing.DefaultTopicRouter;
import de.omnistreamforce.routing.TopicMapping;
import de.omnistreamforce.serializer.EventSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orquesta multiples {@link GenerationEngine} (uno por dominio) en paralelo usando
 * virtual threads (Java 21). Permite anadir y quitar dominios en caliente sin
 * detener los demas y agrega estadisticas en un solo dashboard.
 */
public class MultiDomainEngine {

    private final EventSerializer serializer;
    private final EventPublisher publisher;
    private final DefaultTopicRouter router;
    private final ErrorInjector sharedErrorInjector = new ErrorInjector();
    private final Map<String, DomainEntry> domains = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public MultiDomainEngine(EventSerializer serializer, EventPublisher publisher) {
        this.serializer = serializer;
        this.publisher = publisher;
        this.router = new DefaultTopicRouter(new de.omnistreamforce.routing.DomainTopicConfig(
                List.of(), true, "-errors"));
    }

    public synchronized void addDomain(DomainGenerator generator, GenerationConfig config, TopicMapping mapping) {
        if (domains.containsKey(config.domainName())) {
            throw new IllegalStateException("El dominio ya esta activo: " + config.domainName());
        }
        router.addMapping(mapping);
        GenerationEngine engine = new GenerationEngine(generator, config, serializer, router, publisher, sharedErrorInjector);
        domains.put(config.domainName(), new DomainEntry(generator, config, mapping, engine));
        executor.submit(engine::start);
    }

    public synchronized boolean removeDomain(String domainName) {
        DomainEntry entry = domains.remove(domainName);
        if (entry == null) {
            return false;
        }
        entry.engine.stop();
        router.removeMapping(domainName);
        return true;
    }

    public void startAll() {
        for (DomainEntry entry : domains.values()) {
            entry.engine.start();
        }
    }

    public void pauseAll() {
        domains.values().forEach(e -> e.engine.pause());
    }

    public void resumeAll() {
        domains.values().forEach(e -> e.engine.resume());
    }

    public void stopAll() {
        domains.values().forEach(e -> e.engine.stop());
    }

    public void shutdown() {
        stopAll();
        executor.shutdownNow();
        publisher.close();
    }

    public boolean hasDomain(String domainName) {
        return domains.containsKey(domainName);
    }

    public List<String> activeDomains() {
        return new ArrayList<>(domains.keySet());
    }

    public GenerationEngine getEngine(String domainName) {
        DomainEntry entry = domains.get(domainName);
        return entry == null ? null : entry.engine;
    }

    public List<DomainEntry> domainEntries() {
        return new ArrayList<>(domains.values());
    }

    public AggregateStats aggregateStats() {
        long totalEvents = 0;
        long totalErrors = 0;
        long totalSent = 0;
        long totalBytes = 0;
        Map<String, GenerationStats.Snapshot> perDomain = new LinkedHashMap<>();
        Map<String, TopicStats> perTopic = new LinkedHashMap<>();
        for (DomainEntry entry : domains.values()) {
            GenerationStats.Snapshot s = entry.engine.snapshot();
            totalEvents += s.totalEvents();
            totalErrors += s.totalErrors();
            totalSent += s.totalSent();
            totalBytes += s.totalBytes();
            perDomain.put(entry.config.domainName(), s);
            for (var e : s.perTopicStats().entrySet()) {
                perTopic.merge(e.getKey(), e.getValue(), (a, b) -> new TopicStats(
                        a.totalSent() + b.totalSent(),
                        a.totalAcknowledged() + b.totalAcknowledged(),
                        a.totalFailed() + b.totalFailed(),
                        a.totalErrors() + b.totalErrors(),
                        0.0
                ));
            }
        }
        return new AggregateStats(totalEvents, totalErrors, totalSent, totalBytes,
                Collections.unmodifiableMap(perDomain), Collections.unmodifiableMap(perTopic));
    }

    public record DomainEntry(DomainGenerator generator, GenerationConfig config,
                              TopicMapping mapping, GenerationEngine engine) {
    }

    public record AggregateStats(
            long totalEvents,
            long totalErrors,
            long totalSent,
            long totalBytes,
            Map<String, GenerationStats.Snapshot> perDomain,
            Map<String, TopicStats> perTopic
    ) {
    }
}