package de.omnistreamforce.cli.interactive;

import de.omnistreamforce.cli.console.ConsoleRenderer;
import de.omnistreamforce.engine.GenerationStats;
import de.omnistreamforce.engine.MultiDomainEngine;
import de.omnistreamforce.engine.TopicStats;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El render del panel se comprueba con estadisticas fabricadas, sin arrancar ningun motor.
 */
class PublishingDashboardTest {

    private final ConsoleRenderer console = new ConsoleRenderer(
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), false);

    private MultiDomainEngine.AggregateStats stats() {
        Map<String, GenerationStats.Snapshot> perDomain = new LinkedHashMap<>();
        perDomain.put("fastfood", new GenerationStats.Snapshot(
                1_500, 200, 1_500, 1_200_000, 120.5, 12_000, 3.4,
                Map.of("fastfood-events", new TopicStats(1_300, 1_300, 0, 0, 3.2))));
        perDomain.put("healthcare", new GenerationStats.Snapshot(
                500, 40, 500, 400_000, 40.0, 12_000, 2.1,
                Map.of("healthcare-events", new TopicStats(500, 500, 0, 0, 2.1))));

        Map<String, TopicStats> perTopic = new LinkedHashMap<>();
        perTopic.put("fastfood-events", new TopicStats(1_300, 1_300, 0, 0, 3.2));
        perTopic.put("fastfood-errors", new TopicStats(200, 200, 1, 200, 3.9));
        perTopic.put("healthcare-events", new TopicStats(500, 500, 0, 40, 2.1));

        return new MultiDomainEngine.AggregateStats(2_000, 240, 2_000, 1_600_000, perDomain, perTopic);
    }

    @Test
    void everyActiveDomainHasItsOwnRow() {
        String rendered = new PublishingDashboard(null, console, 0).render(stats(), 12);

        assertThat(rendered).contains("fastfood").contains("healthcare");
        assertThat(rendered).contains("120.5").contains("40.0");
        assertThat(rendered).contains("1.5K");     // 1500 eventos humanizados
    }

    @Test
    void topicsAreBrokenDownSeparately() {
        String rendered = new PublishingDashboard(null, console, 0).render(stats(), 12);

        assertThat(rendered).contains("fastfood-events")
                .contains("fastfood-errors")
                .contains("healthcare-events");
    }

    @Test
    void totalsAndControlsAreAlwaysVisible() {
        String rendered = new PublishingDashboard(null, console, 0).render(stats(), 12);

        assertThat(rendered).contains("TOTAL: 2.0K eventos | 240 errores");
        assertThat(rendered).contains("[P] pausar").contains("[S] parar").contains("[Q] salir");
    }

    @Test
    void progressBarOnlyAppearsWhenTheRunIsTimeBoxed() {
        String unlimited = new PublishingDashboard(null, console, 0).render(stats(), 30);
        assertThat(unlimited).doesNotContain("%").contains("Tiempo: 30s");

        String limited = new PublishingDashboard(null, console, 60).render(stats(), 30);
        assertThat(limited).contains("Tiempo: 30s / 60s").contains("50%");
    }

    @Test
    void anEmptyRunStillRenders() {
        MultiDomainEngine.AggregateStats empty = new MultiDomainEngine.AggregateStats(
                0, 0, 0, 0, Map.of(), Map.of());

        String rendered = new PublishingDashboard(null, console, 0).render(empty, 0);

        assertThat(rendered).contains("TOTAL: 0 eventos");
        assertThat(rendered).contains("Dominio");
    }
}
