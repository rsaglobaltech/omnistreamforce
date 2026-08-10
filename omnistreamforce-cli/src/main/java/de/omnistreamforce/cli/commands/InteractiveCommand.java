package de.omnistreamforce.cli.commands;

import de.omnistreamforce.cli.cluster.KafkaClusterGateway;
import de.omnistreamforce.cli.console.ConsoleRenderer;
import de.omnistreamforce.cli.console.Prompter;
import de.omnistreamforce.cli.interactive.InteractiveSession;
import de.omnistreamforce.cli.interactive.PublishingDashboard;
import de.omnistreamforce.cli.interactive.SessionPlan;
import de.omnistreamforce.domain.DomainGenerator;
import de.omnistreamforce.domain.DomainRegistry;
import de.omnistreamforce.engine.EventPublisher;
import de.omnistreamforce.engine.GenerationConfig;
import de.omnistreamforce.engine.MultiDomainEngine;
import de.omnistreamforce.persistence.PublisherFactory;
import de.omnistreamforce.persistence.SinkConfig;
import de.omnistreamforce.routing.TopicMapping;
import de.omnistreamforce.serializer.EventSerializer;
import de.omnistreamforce.serializer.SerializerFactory;
import picocli.CommandLine.Command;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Flujo interactivo completo: pregunta, configura, publica y muestra el panel en vivo.
 */
@Command(
        name = "interactive",
        description = "Flujo guiado: conexion, topics, dominios, destino y publicacion en vivo."
)
public class InteractiveCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        ConsoleRenderer console = new ConsoleRenderer();
        Prompter prompt = new Prompter();
        DomainRegistry registry = new DomainRegistry();

        InteractiveSession session = new InteractiveSession(prompt, console, registry,
                KafkaClusterGateway::new);

        SessionPlan plan;
        try {
            plan = session.run();
        } catch (Prompter.AbortedException e) {
            console.warn("Sesion cancelada: " + e.getMessage());
            closeQuietly(session);
            return 0;
        } catch (RuntimeException e) {
            console.error(e.getMessage() == null ? e.toString() : e.getMessage());
            closeQuietly(session);
            return 1;
        }

        // el gateway solo servia para explorar el cluster; el publisher abre su propia conexion
        closeQuietly(session);
        return publish(plan, console);
    }

    private int publish(SessionPlan plan, ConsoleRenderer console) {
        DomainRegistry registry = new DomainRegistry();
        EventSerializer serializer = SerializerFactory.create(plan.serializerFormat());

        SinkConfig sinkConfig = SinkConfig.builder(plan.sinkType())
                .kafka(plan.kafka())
                .persistence(plan.persistence())
                .keyStrategy(plan.keyStrategy())
                .keyField(plan.keyField())
                .serializerFormat(plan.serializerFormat())
                .build();

        EventPublisher publisher = PublisherFactory.create(sinkConfig);
        MultiDomainEngine engine = new MultiDomainEngine(serializer, publisher);
        PublishingDashboard dashboard = new PublishingDashboard(engine, console, plan.durationSeconds());
        AtomicBoolean stopped = new AtomicBoolean();

        Runnable shutdown = () -> {
            if (stopped.compareAndSet(false, true)) {
                dashboard.stop();
                engine.stopAll();
                engine.shutdown();
                printSummary(console, engine);
            }
        };
        Thread hook = new Thread(shutdown, "osf-cli-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);

        for (SessionPlan.DomainPlan domainPlan : plan.domains()) {
            DomainGenerator generator = registry.get(domainPlan.domain()).orElseThrow(
                    () -> new IllegalStateException("Dominio no disponible: " + domainPlan.domain()));

            GenerationConfig config = new GenerationConfig(
                    domainPlan.domain(), domainPlan.topic(), domainPlan.errorTopic(),
                    domainPlan.eventsPerSecond(), domainPlan.errorRate(), plan.publishingMode(),
                    plan.durationSeconds(), plan.keyStrategy(), plan.keyField(), 0, 0);
            TopicMapping mapping = new TopicMapping(domainPlan.domain(), domainPlan.topic(),
                    domainPlan.errorTopic(), 3, (short) 1, true);
            engine.addDomain(generator, config, mapping);
        }

        dashboard.start();
        awaitEnd(console, engine, plan.durationSeconds());

        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // ya estamos apagando: el hook se encarga
        }
        shutdown.run();
        return 0;
    }

    /**
     * Espera al final de la publicacion.
     * <p>
     * Los controles de teclado corren aparte porque la entrada puede no ser interactiva (una
     * tuberia, un script de CI): si ahi se esperase al teclado, el proceso terminaria al instante
     * sin haber publicado nada. Con duracion fijada se espera a que el motor la agote; sin ella,
     * hasta que el usuario pulse S o Q.
     */
    private void awaitEnd(ConsoleRenderer console, MultiDomainEngine engine, long durationSeconds) {
        CountDownLatch stopRequested = new CountDownLatch(1);
        Thread controls = new Thread(() -> readControls(console, engine, stopRequested), "osf-cli-controls");
        controls.setDaemon(true);
        controls.start();

        try {
            if (durationSeconds > 0) {
                // margen para que el ultimo tick y el flush del publisher terminen
                stopRequested.await(durationSeconds + 2, TimeUnit.SECONDS);
            } else {
                stopRequested.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Bucle de teclado; termina con S o Q, o cuando se agota la entrada. */
    private void readControls(ConsoleRenderer console, MultiDomainEngine engine,
                              CountDownLatch stopRequested) {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                switch (line.trim().toLowerCase(Locale.ROOT)) {
                    case "p" -> engine.pauseAll();
                    case "r" -> engine.resumeAll();
                    case "s", "q" -> {
                        stopRequested.countDown();
                        return;
                    }
                    default -> {
                        // cualquier otra tecla solo refresca el panel
                    }
                }
            }
        } catch (Exception e) {
            console.error("Error leyendo la entrada: " + e.getMessage());
        }
        // entrada agotada (tuberia cerrada): no se fuerza la parada, manda la duracion
    }

    private void printSummary(ConsoleRenderer console, MultiDomainEngine engine) {
        MultiDomainEngine.AggregateStats stats = engine.aggregateStats();
        console.title("Resumen final");
        console.info("Eventos generados: " + stats.totalEvents());
        console.info("De ellos, errores: " + stats.totalErrors());
        console.info("Bytes serializados: " + stats.totalBytes());
        stats.perTopic().forEach((topic, topicStats) ->
                console.info(topic + ": " + topicStats.totalSent() + " enviados, "
                        + topicStats.totalFailed() + " fallidos"));
    }

    private void closeQuietly(InteractiveSession session) {
        if (session.gateway() != null) {
            try {
                session.gateway().close();
            } catch (RuntimeException ignored) {
                // cerrar el explorador del cluster nunca debe tumbar el CLI
            }
        }
    }
}
