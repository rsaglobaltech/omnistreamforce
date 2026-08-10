package de.omnistreamforce.engine;

import de.omnistreamforce.core.Severity;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MultiDomainEngine} comparte una sola instancia de {@link ErrorInjector} entre
 * todos los dominios activos, cada uno con su propio hilo: estos tests cubren ese uso.
 */
class ErrorInjectorConcurrencyTest {

    @Test
    void nextErrorTypeIsSafeUnderConcurrentUse() throws Exception {
        ErrorInjector injector = new ErrorInjector();
        StubDomainGenerator generator = new StubDomainGenerator();
        int threads = 8;
        int perThread = 2000;
        Set<String> seen = ConcurrentHashMap.newKeySet();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(threads);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < perThread; i++) {
                            seen.add(injector.nextErrorType(generator));
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failure.get()).isNull();
        assertThat(seen).containsExactlyInAnyOrderElementsOf(generator.getErrorTypes());
        assertThat(injector.allErrorTypesGenerated(generator)).isTrue();
    }

    @Test
    void severityDistributionCanBeReplacedWhileOtherThreadsRead() throws Exception {
        ErrorInjector injector = new ErrorInjector();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch readersDone = new CountDownLatch(4);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < 4; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < 5000; i++) {
                            assertThat(injector.nextSeverity()).isNotNull();
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        readersDone.countDown();
                    }
                });
            }
            for (int i = 0; i < 200; i++) {
                Map<Severity, Double> distribution = new EnumMap<>(Severity.class);
                distribution.put(Severity.LOW, 0.5);
                distribution.put(Severity.CRITICAL, 0.5);
                injector.setSeverityDistribution(distribution);
            }
            assertThat(readersDone.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failure.get()).isNull();
    }

    @Test
    void emptyOrNullDistributionKeepsThePreviousOne() {
        ErrorInjector injector = new ErrorInjector();
        Map<Severity, Double> onlyCritical = new EnumMap<>(Severity.class);
        onlyCritical.put(Severity.CRITICAL, 1.0);
        injector.setSeverityDistribution(onlyCritical);

        injector.setSeverityDistribution(null);
        injector.setSeverityDistribution(new EnumMap<>(Severity.class));

        assertThat(injector.nextSeverity()).isEqualTo(Severity.CRITICAL);
    }
}
