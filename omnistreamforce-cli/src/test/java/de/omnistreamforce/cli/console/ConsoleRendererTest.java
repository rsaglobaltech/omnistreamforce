package de.omnistreamforce.cli.console;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleRendererTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ConsoleRenderer plain = new ConsoleRenderer(
            new PrintStream(output, true, StandardCharsets.UTF_8), false);

    private String printed() {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void tableColumnsAreWideEnoughForTheirContent() {
        String table = plain.renderTable(
                List.of("Dominio", "Topic"),
                List.of(List.of("fastfood", "fastfood-events"),
                        List.of("healthcare", "healthcare-events")));

        assertThat(table).contains("| Dominio    | Topic             |");
        assertThat(table).contains("| fastfood   | fastfood-events   |");
        assertThat(table).contains("| healthcare | healthcare-events |");
    }

    @Test
    void missingOrNullCellsDoNotBreakTheTable() {
        String table = plain.renderTable(List.of("A", "B", "C"),
                List.of(List.of("uno"), java.util.Arrays.asList("dos", null, "tres")));

        assertThat(table).contains("| uno |");
        // separador, cabecera, separador, dos filas y separador de cierre
        assertThat(table.lines().count()).isEqualTo(6);
    }

    @Test
    void withoutAnsiSupportNoEscapeCodesAreEmitted() {
        plain.success("todo bien");
        plain.error("algo falla");
        plain.title("Resumen");

        assertThat(printed()).doesNotContain("[");
        assertThat(printed()).contains("[OK] todo bien").contains("[ERROR] algo falla");
    }

    @Test
    void ansiRendererEmitsColourCodes() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ConsoleRenderer coloured = new ConsoleRenderer(
                new PrintStream(buffer, true, StandardCharsets.UTF_8), true);

        coloured.success("hecho");

        assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("[32m");
    }

    @Test
    void progressBarIsClampedBetweenZeroAndOne() {
        assertThat(plain.progressBar(0.0, 10)).isEqualTo("[----------]   0%");
        assertThat(plain.progressBar(0.5, 10)).isEqualTo("[#####-----]  50%");
        assertThat(plain.progressBar(1.0, 10)).isEqualTo("[##########] 100%");
        assertThat(plain.progressBar(2.5, 10)).isEqualTo("[##########] 100%");
        assertThat(plain.progressBar(-1.0, 10)).isEqualTo("[----------]   0%");
    }

    @Test
    void largeNumbersAreHumanized() {
        assertThat(ConsoleRenderer.humanize(999)).isEqualTo("999");
        assertThat(ConsoleRenderer.humanize(1_500)).isEqualTo("1.5K");
        assertThat(ConsoleRenderer.humanize(2_300_000)).isEqualTo("2.3M");
    }

    @Test
    void clearWithoutAnsiJustSeparatesOutput() {
        plain.clear();
        assertThat(printed()).doesNotContain("[");
    }
}
