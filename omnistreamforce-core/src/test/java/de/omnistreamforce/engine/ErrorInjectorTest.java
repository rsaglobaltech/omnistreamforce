package de.omnistreamforce.engine;

import de.omnistreamforce.core.Severity;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorInjectorTest {

    @Test
    void shouldGenerateErrorRespectsRate() {
        ErrorInjector injector = new ErrorInjector();
        int errors = 0;
        for (int i = 0; i < 1000; i++) {
            if (injector.shouldGenerateError(25.0)) {
                errors++;
            }
        }
        assertThat(errors).isBetween(180, 320);
    }

    @Test
    void zeroErrorRateNeverErrors() {
        ErrorInjector injector = new ErrorInjector();
        for (int i = 0; i < 1000; i++) {
            assertThat(injector.shouldGenerateError(0.0)).isFalse();
        }
    }

    @Test
    void hundredPercentRateAlwaysErrors() {
        ErrorInjector injector = new ErrorInjector();
        for (int i = 0; i < 1000; i++) {
            assertThat(injector.shouldGenerateError(100.0)).isTrue();
        }
    }

    @Test
    void nextErrorTypeEventuallyCoversAllTypes() {
        ErrorInjector injector = new ErrorInjector();
        StubDomainGenerator generator = new StubDomainGenerator();
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            generated.add(injector.nextErrorType(generator));
            if (generated.size() == generator.getErrorTypes().size()) {
                break;
            }
        }
        assertThat(generated).containsExactlyInAnyOrderElementsOf(generator.getErrorTypes());
    }

    @Test
    void theSeverityChosenByTheDomainIsNotOverwritten() {
        // un fallo de frenos o una rotura de cadena de frio son criticos por su naturaleza:
        // la distribucion configurada no debe degradarlos
        de.omnistreamforce.core.Event critical = new de.omnistreamforce.core.Event(
                "id", "ERROR", "autos", "test", 1L, "1.0", Map.of(),
                Map.of("errorType", "BrakeFailure", "severity", "CRITICAL"), "t", "c");

        de.omnistreamforce.core.Event result = ErrorInjector.withSeverity(critical, Severity.LOW);

        assertThat(result.metadata().get("severity")).isEqualTo("CRITICAL");
        assertThat(result).isSameAs(critical);
    }

    @Test
    void severityIsAppliedWhenTheDomainDoesNotSetOne() {
        de.omnistreamforce.core.Event bare = new de.omnistreamforce.core.Event(
                "id", "ERROR", "stub", "test", 1L, "1.0", Map.of(),
                Map.of("errorType", "Whatever"), "t", "c");

        de.omnistreamforce.core.Event result = ErrorInjector.withSeverity(bare, Severity.HIGH);

        assertThat(result.metadata().get("severity")).isEqualTo("HIGH");
    }

    @Test
    void customSeverityDistributionIsRespected() {
        ErrorInjector injector = new ErrorInjector();
        Map<Severity, Double> dist = new EnumMap<>(Severity.class);
        dist.put(Severity.CRITICAL, 1.0);
        injector.setSeverityDistribution(dist);
        for (int i = 0; i < 20; i++) {
            assertThat(injector.nextSeverity()).isEqualTo(Severity.CRITICAL);
        }
    }
}