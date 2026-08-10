package de.omnistreamforce.cli.interactive;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.engine.EventPublisher;
import de.omnistreamforce.engine.GenerationConfig;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.engine.MultiDomainEngine;
import de.omnistreamforce.engine.PublishingMode;
import de.omnistreamforce.domain.DomainGenerator;
import de.omnistreamforce.domain.DomainRegistry;
import de.omnistreamforce.routing.TopicMapping;
import de.omnistreamforce.serializer.JsonEventSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los controles se ejercitan sobre un motor real con un publisher que descarta, sin Kafka.
 */
class PublishingControlsTest {

    private static final String TOPIC = "controls-test";

    /** Publisher que no hace nada: aqui interesa el efecto sobre el motor, no la salida. */
    private static final EventPublisher DISCARDING = new EventPublisher() {
        @Override
        public void publish(Event event, String topic) {
        }

        @Override
        public void close() {
        }
    };

    private MultiDomainEngine engine;
    private CountDownLatch stopRequested;

    @BeforeEach
    void setUp() {
        engine = new MultiDomainEngine(new JsonEventSerializer(), DISCARDING);
        stopRequested = new CountDownLatch(1);

        DomainGenerator generator = new DomainRegistry().listGenerators().iterator().next();
        String domain = generator.getDomainName();
        engine.addDomain(generator,
                new GenerationConfig(domain, TOPIC, TOPIC, 100, 10.0, PublishingMode.STEADY,
                        0, KeyStrategy.RANDOM, null, 0, 0),
                new TopicMapping(domain, TOPIC, TOPIC, 1, (short) 1, false));
    }

    @AfterEach
    void tearDown() {
        engine.stopAll();
        engine.shutdown();
    }

    private PublishingControls controls(String... commands) {
        String script = commands.length == 0 ? "" :
                String.join(System.lineSeparator(), commands) + System.lineSeparator();
        return new PublishingControls(engine, new BufferedReader(new StringReader(script)), stopRequested);
    }

    private int totalEps() {
        return engine.domainEntries().stream()
                .mapToInt(entry -> entry.engine().currentEventsPerSecond())
                .sum();
    }

    @Test
    void pauseAndResumeReachTheEngine() {
        PublishingControls controls = controls();

        controls.handle("p");
        assertThat(controls.isPaused()).isTrue();
        assertThat(controls.lastAction()).isEqualTo("pausado");
        assertThat(engine.domainEntries()).allMatch(entry -> entry.engine().isPaused());

        controls.handle("r");
        assertThat(controls.isPaused()).isFalse();
        assertThat(engine.domainEntries()).noneMatch(entry -> entry.engine().isPaused());
    }

    @Test
    void plusAndMinusChangeTheRate() {
        PublishingControls controls = controls();
        assertThat(totalEps()).isEqualTo(100);

        controls.handle("+");
        assertThat(totalEps()).isEqualTo(125);
        assertThat(controls.lastAction()).contains("125");

        controls.handle("-");
        assertThat(totalEps()).isEqualTo(94);
    }

    @Test
    void theRateNeverDropsBelowOne() {
        PublishingControls controls = controls();
        for (int i = 0; i < 40; i++) {
            controls.handle("-");
        }
        assertThat(totalEps()).isEqualTo(1);
    }

    @Test
    void errorRateCanBeSetOnTheFly() {
        PublishingControls controls = controls();

        controls.handle("e 42");

        assertThat(controls.lastAction()).contains("42.0%");
        assertThat(engine.domainEntries()).allMatch(entry -> entry.engine().currentErrorRate() == 42.0);
    }

    @Test
    void anInvalidErrorRateIsReportedWithoutChangingAnything() {
        PublishingControls controls = controls();

        controls.handle("e 200");
        assertThat(controls.lastAction()).contains("entre 0 y 100");

        controls.handle("e abc");
        assertThat(controls.lastAction()).contains("no es un porcentaje valido");

        controls.handle("e");
        assertThat(controls.lastAction()).contains("usa 'e <porcentaje>'");

        assertThat(engine.domainEntries()).allMatch(entry -> entry.engine().currentErrorRate() == 10.0);
    }

    @Test
    void stopAndQuitReleaseTheLatch() {
        assertThat(controls().handle("s")).isFalse();
        assertThat(stopRequested.getCount()).isZero();

        CountDownLatch other = new CountDownLatch(1);
        PublishingControls quitting = new PublishingControls(engine,
                new BufferedReader(new StringReader("")), other);
        assertThat(quitting.handle("q")).isFalse();
        assertThat(other.getCount()).isZero();
    }

    @Test
    void unknownAndEmptyCommandsAreHarmless() {
        PublishingControls controls = controls();

        assertThat(controls.handle("")).isTrue();
        assertThat(controls.handle("   ")).isTrue();
        assertThat(controls.handle("z")).isTrue();
        assertThat(controls.lastAction()).contains("comando desconocido: z");
        assertThat(totalEps()).isEqualTo(100);
    }

    @Test
    void theRunLoopAppliesAScriptOfCommands() throws Exception {
        PublishingControls controls = controls("p", "+", "e 30", "s", "r");
        controls.run();

        assertThat(controls.isPaused()).isTrue();          // la 'r' esta despues de la parada
        assertThat(totalEps()).isEqualTo(125);
        assertThat(engine.domainEntries()).allMatch(entry -> entry.engine().currentErrorRate() == 30.0);
        assertThat(stopRequested.await(1, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * Los comandos del panel llegan detras de las respuestas del flujo interactivo. Si cada parte
     * usa su propio lector sobre la misma entrada, el buffer del primero se queda con lo que venia
     * detras y los comandos no llegan nunca: por eso el reader se comparte.
     */
    @Test
    void commandsSurviveAfterThePromptsHaveReadFromTheSameInput() throws Exception {
        BufferedReader shared = new BufferedReader(new StringReader(String.join(
                System.lineSeparator(),
                "localhost:9092", "PLAINTEXT",    // respuestas de la configuracion
                "p", "e 30", "s") + System.lineSeparator()));

        // el flujo interactivo consume sus respuestas del mismo lector
        assertThat(shared.readLine()).isEqualTo("localhost:9092");
        assertThat(shared.readLine()).isEqualTo("PLAINTEXT");

        PublishingControls controls = new PublishingControls(engine, shared, stopRequested);
        controls.run();

        assertThat(controls.isPaused()).isTrue();
        assertThat(engine.domainEntries()).allMatch(entry -> entry.engine().currentErrorRate() == 30.0);
        assertThat(stopRequested.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void commandsAreCaseInsensitiveAndToleratePadding() {
        PublishingControls controls = controls();

        controls.handle("  P  ");
        assertThat(controls.isPaused()).isTrue();

        controls.handle("R");
        assertThat(controls.isPaused()).isFalse();

        controls.handle("E 5,5");
        assertThat(engine.domainEntries()).allMatch(entry -> entry.engine().currentErrorRate() == 5.5);
    }
}
