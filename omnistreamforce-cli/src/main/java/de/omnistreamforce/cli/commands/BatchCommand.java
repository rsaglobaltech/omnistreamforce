package de.omnistreamforce.cli.commands;

import de.omnistreamforce.cli.config.ConfigTranslator;
import de.omnistreamforce.cli.console.ConsoleRenderer;
import de.omnistreamforce.config.ConfigManager;
import de.omnistreamforce.config.OmniStreamForceConfig;
import de.omnistreamforce.config.ProfileLoader;
import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.DomainGenerator;
import de.omnistreamforce.domain.DomainRegistry;
import de.omnistreamforce.engine.EventPublisher;
import de.omnistreamforce.engine.MultiDomainEngine;
import de.omnistreamforce.persistence.PublisherFactory;
import de.omnistreamforce.serializer.EventSerializer;
import de.omnistreamforce.serializer.SerializerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ejecucion sin interaccion a partir de un fichero YAML o de un perfil. Pensado para CI y para
 * dejar el generador corriendo con una configuracion fija.
 */
@Command(
        name = "batch",
        description = "Publica sin interaccion usando un fichero de configuracion o un perfil."
)
public class BatchCommand implements Callable<Integer> {

    private static final long PROGRESS_INTERVAL_MS = 5_000;

    @Option(names = {"-c", "--config"}, description = "Fichero YAML de configuracion")
    Path configFile;

    @Option(names = {"-P", "--profile"}, description = "Perfil predefinido (ver list-profiles)")
    String profile;

    @Option(names = {"--dry-run"}, description = "Genera eventos y los cuenta, sin publicar")
    boolean dryRun;

    @Option(names = {"--duration"}, description = "Sobrescribe la duracion en segundos")
    Long durationOverride;

    @Option(names = {"--eps"}, description = "Sobrescribe los eventos por segundo de todos los dominios")
    Integer epsOverride;

    @Option(names = {"--sample"}, description = "En dry-run, cuantos eventos mostrar", defaultValue = "2")
    int sampleSize;

    @Override
    public Integer call() {
        ConsoleRenderer console = new ConsoleRenderer();
        ConfigManager manager = new ConfigManager();

        OmniStreamForceConfig config;
        try {
            config = applyOverrides(loadConfig(manager));
        } catch (RuntimeException e) {
            console.error(e.getMessage() == null ? e.toString() : e.getMessage());
            return 1;
        }

        List<String> errors = manager.validate(config);
        if (!errors.isEmpty()) {
            console.error("La configuracion no es valida:");
            errors.forEach(error -> console.info("- " + error));
            return 1;
        }

        showPlan(console, config);
        return dryRun ? dryRun(console, config) : publish(console, config);
    }

    private OmniStreamForceConfig loadConfig(ConfigManager manager) {
        if (configFile == null && profile == null) {
            throw new IllegalArgumentException("Indica --config <fichero> o --profile <nombre>");
        }
        OmniStreamForceConfig fromProfile = profile == null
                ? null : new ProfileLoader().loadProfile(profile);
        OmniStreamForceConfig fromFile = configFile == null ? null : manager.load(configFile);
        // el fichero manda sobre el perfil, para poder ajustar un perfil sin copiarlo entero
        return fromProfile == null ? fromFile
                : (fromFile == null ? fromProfile : manager.merge(fromProfile, fromFile));
    }

    private OmniStreamForceConfig applyOverrides(OmniStreamForceConfig config) {
        if (durationOverride == null && epsOverride == null) {
            return config;
        }
        List<OmniStreamForceConfig.DomainMapping> domains = new ArrayList<>();
        for (OmniStreamForceConfig.DomainMapping domain : config.domains()) {
            domains.add(epsOverride == null ? domain : new OmniStreamForceConfig.DomainMapping(
                    domain.domain(), domain.topic(), domain.errorTopic(), epsOverride,
                    domain.errorRate(), domain.keyStrategy(), domain.keyField()));
        }
        OmniStreamForceConfig.GenerationSection generation = durationOverride == null
                ? config.generation()
                : new OmniStreamForceConfig.GenerationSection(config.generation().publishingMode(),
                durationOverride, config.generation().burstSize(), config.generation().rampTargetEPS());
        return new OmniStreamForceConfig(config.kafka(), domains, generation,
                config.serialization(), config.sink(), config.topic());
    }

    private void showPlan(ConsoleRenderer console, OmniStreamForceConfig config) {
        console.title(dryRun ? "Simulacion (dry-run)" : "Publicacion en modo batch");
        List<List<String>> rows = new ArrayList<>();
        for (OmniStreamForceConfig.DomainMapping domain : config.domains()) {
            rows.add(List.of(domain.domain(), domain.topic(), domain.resolvedErrorTopic(),
                    String.valueOf(domain.eventsPerSecond()),
                    String.format(Locale.ROOT, "%.1f%%", domain.errorRate())));
        }
        console.table(List.of("Dominio", "Topic", "Topic de errores", "EPS", "Errores"), rows);
        console.info("Destino: " + config.sink().type()
                + " | formato: " + config.serialization().format()
                + " | modo: " + config.generation().publishingMode()
                + " | duracion: " + (config.generation().durationSeconds() == 0
                ? "ilimitada" : config.generation().durationSeconds() + "s"));
    }

    /** Genera de verdad, con los generadores reales, pero sin abrir ninguna conexion. */
    private int dryRun(ConsoleRenderer console, OmniStreamForceConfig config) {
        DomainRegistry registry = new DomainRegistry();
        EventSerializer serializer = SerializerFactory.create(config.serialization().format());
        long seconds = config.generation().durationSeconds() > 0
                ? config.generation().durationSeconds() : 1;

        List<List<String>> rows = new ArrayList<>();
        for (OmniStreamForceConfig.DomainMapping domain : config.domains()) {
            DomainGenerator generator = registry.get(domain.domain()).orElse(null);
            if (generator == null) {
                console.error("Dominio no disponible en el classpath: " + domain.domain());
                return 1;
            }
            long total = (long) domain.eventsPerSecond() * seconds;
            long errors = Math.round(total * domain.errorRate() / 100.0);

            Event sample = generator.generateEvent(generator.getSupportedEventTypes().get(0));
            long bytes = serializer.serialize(sample).length;

            rows.add(List.of(domain.domain(), String.valueOf(total), String.valueOf(errors),
                    bytes + " B", ConsoleRenderer.humanize(total * bytes) + " B"));
        }
        console.title("Estimacion para " + seconds + "s");
        console.table(List.of("Dominio", "Eventos", "De ellos error", "Tamano medio", "Volumen"), rows);

        for (OmniStreamForceConfig.DomainMapping domain : config.domains()) {
            DomainGenerator generator = registry.get(domain.domain()).orElseThrow();
            console.title("Muestra de " + domain.domain());
            for (int i = 0; i < Math.max(1, sampleSize); i++) {
                Event event = i % 2 == 0
                        ? generator.generateEvent(generator.getSupportedEventTypes().get(
                        i % generator.getSupportedEventTypes().size()))
                        : generator.generateErrorEvent();
                console.println(new String(serializer.serialize(event), StandardCharsets.UTF_8));
            }
        }
        console.success("Dry-run completado: no se ha publicado nada");
        return 0;
    }

    private int publish(ConsoleRenderer console, OmniStreamForceConfig config) {
        DomainRegistry registry = new DomainRegistry();
        EventSerializer serializer = SerializerFactory.create(config.serialization().format());
        EventPublisher publisher = PublisherFactory.create(ConfigTranslator.toSinkConfig(config));
        MultiDomainEngine engine = new MultiDomainEngine(serializer, publisher);

        AtomicBoolean stopped = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        Runnable shutdown = () -> {
            if (stopped.compareAndSet(false, true)) {
                engine.stopAll();
                engine.shutdown();
                printSummary(console, engine);
                finished.countDown();
            }
        };
        Thread hook = new Thread(shutdown, "osf-batch-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);

        for (OmniStreamForceConfig.DomainMapping domain : config.domains()) {
            DomainGenerator generator = registry.get(domain.domain()).orElse(null);
            if (generator == null) {
                console.error("Dominio no disponible en el classpath: " + domain.domain());
                shutdown.run();
                return 1;
            }
            engine.addDomain(generator,
                    ConfigTranslator.toGenerationConfig(config, domain),
                    ConfigTranslator.toTopicMapping(config, domain));
        }

        long duration = config.generation().durationSeconds();
        console.success("Publicando" + (duration == 0 ? " (Ctrl+C para parar)" : " durante " + duration + "s"));
        reportProgress(console, engine, duration);

        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // ya estamos apagando: del resumen se encarga el hook
        }
        shutdown.run();
        return 0;
    }

    /** Traza de progreso periodica: en batch no hay panel interactivo que refrescar. */
    private void reportProgress(ConsoleRenderer console, MultiDomainEngine engine, long duration) {
        long deadline = duration > 0
                ? System.currentTimeMillis() + duration * 1000 + 2000 : Long.MAX_VALUE;
        try {
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(PROGRESS_INTERVAL_MS);
                MultiDomainEngine.AggregateStats stats = engine.aggregateStats();
                console.info(String.format(Locale.ROOT,
                        "%s eventos | %s errores | %s bytes",
                        ConsoleRenderer.humanize(stats.totalEvents()),
                        ConsoleRenderer.humanize(stats.totalErrors()),
                        ConsoleRenderer.humanize(stats.totalBytes())));
                if (duration > 0 && stats.totalEvents() > 0 && allDomainsStopped(engine)) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean allDomainsStopped(MultiDomainEngine engine) {
        return engine.domainEntries().stream().noneMatch(entry -> entry.engine().isRunning());
    }

    private void printSummary(ConsoleRenderer console, MultiDomainEngine engine) {
        MultiDomainEngine.AggregateStats stats = engine.aggregateStats();
        console.title("Resumen");
        console.info("Eventos generados: " + stats.totalEvents());
        console.info("De ellos, errores: " + stats.totalErrors());
        stats.perTopic().forEach((topic, topicStats) -> console.info(
                topic + ": " + topicStats.totalSent() + " enviados, "
                        + topicStats.totalFailed() + " fallidos"));
    }

}
