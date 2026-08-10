package de.omnistreamforce.cli.console;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrompterTest {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    private Prompter prompter(String... answers) {
        String script = String.join(System.lineSeparator(), answers) + System.lineSeparator();
        return new Prompter(new BufferedReader(new StringReader(script)),
                new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    private String printed() {
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void emptyAnswerTakesTheDefault() {
        assertThat(prompter("").ask("Bootstrap", "localhost:9092")).isEqualTo("localhost:9092");
    }

    @Test
    void answerOverridesTheDefault() {
        assertThat(prompter("broker:9092").ask("Bootstrap", "localhost:9092")).isEqualTo("broker:9092");
    }

    @Test
    void requiredQuestionInsistsUntilAnswered() {
        assertThat(prompter("", "  ", "valor").askRequired("Campo")).isEqualTo("valor");
        assertThat(printed()).contains("(requerido)");
    }

    @Test
    void invalidNumberIsRejectedAndAskedAgain() {
        assertThat(prompter("abc", "42").askInt("EPS", 10, 1, 100)).isEqualTo(42);
        assertThat(printed()).contains("No es un numero valido: abc");
    }

    @Test
    void numberOutOfRangeIsRejected() {
        assertThat(prompter("500", "50").askInt("EPS", 10, 1, 100)).isEqualTo(50);
        assertThat(printed()).contains("Debe estar entre 1 y 100");
    }

    @Test
    void decimalsAcceptCommaAsSeparator() {
        assertThat(prompter("12,5").askDouble("Tasa", 10.0, 0.0, 100.0)).isEqualTo(12.5);
    }

    @Test
    void confirmUnderstandsSpanishAndEnglish() {
        assertThat(prompter("s").confirm("Seguro?", false)).isTrue();
        assertThat(prompter("yes").confirm("Seguro?", false)).isTrue();
        assertThat(prompter("n").confirm("Seguro?", true)).isFalse();
        assertThat(prompter("").confirm("Seguro?", true)).isTrue();
    }

    @Test
    void confirmInsistsOnGarbage() {
        assertThat(prompter("quizas", "n").confirm("Seguro?", true)).isFalse();
        assertThat(printed()).contains("Responde s o n");
    }

    @Test
    void chooseReturnsZeroBasedIndex() {
        assertThat(prompter("2").choose("Formato", List.of("JSON", "AVRO", "PROTOBUF"), 0)).isEqualTo(1);
    }

    @Test
    void chooseFallsBackToTheDefaultOption() {
        assertThat(prompter("").choose("Formato", List.of("JSON", "AVRO"), 1)).isEqualTo(1);
    }

    @Test
    void chooseManyParsesCommaSeparatedNumbersWithoutDuplicates() {
        assertThat(prompter("1,3,3").chooseMany("Dominios",
                List.of("fastfood", "healthcare", "ecommerce"), List.of()))
                .containsExactly(0, 2);
    }

    @Test
    void chooseManyRejectsOutOfRangeAndAsksAgain() {
        assertThat(prompter("9", "2").chooseMany("Dominios",
                List.of("fastfood", "healthcare"), List.of())).containsExactly(1);
        assertThat(printed()).contains("Seleccion invalida");
    }

    @Test
    void endOfInputAbortsInsteadOfLooping() {
        Prompter prompter = new Prompter(new BufferedReader(new StringReader("")),
                new PrintStream(output, true, StandardCharsets.UTF_8));
        assertThatThrownBy(() -> prompter.askRequired("Campo"))
                .isInstanceOf(Prompter.AbortedException.class);
    }
}
