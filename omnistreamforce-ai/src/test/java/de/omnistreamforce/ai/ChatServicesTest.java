package de.omnistreamforce.ai;

import de.omnistreamforce.core.EventSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comportamiento de los dos proveedores sin tocar la red.
 */
class ChatServicesTest {

    private AIConfig openAiConfig() {
        return AIConfig.builder(AIProvider.OPENAI)
                .apiKey("sk-test")
                .model("gpt-4o-mini")
                .maxRetries(2)
                .retryBackoffMs(1)
                .build();
    }

    private AIConfig ollamaConfig() {
        return AIConfig.builder(AIProvider.OLLAMA)
                .model("llama3")
                .maxRetries(1)
                .retryBackoffMs(1)
                .build();
    }

    private String openAiReply(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + content.replace("\"", "\\\"").replace("\n", "\\n") + "\"}}]}";
    }

    private String ollamaReply(String content) {
        return "{\"message\":{\"role\":\"assistant\",\"content\":\""
                + content.replace("\"", "\\\"").replace("\n", "\\n") + "\"}}";
    }

    @Test
    void openAiSendsTheApiKeyAndTheConfiguredModel() {
        FakeHttpTransport transport = new FakeHttpTransport().respondWith(200, openAiReply("hola"));
        OpenAIService service = new OpenAIService(openAiConfig(), transport);

        assertThat(service.chat("eres util", "di hola")).isEqualTo("hola");

        FakeHttpTransport.Call call = transport.calls.get(0);
        assertThat(call.url()).isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(call.headers()).containsEntry("Authorization", "Bearer sk-test");
        assertThat(call.body()).contains("\"model\":\"gpt-4o-mini\"")
                .contains("\"role\":\"system\"").contains("\"role\":\"user\"");
    }

    @Test
    void ollamaNeedsNoCredentialsAndAsksForACompleteResponse() {
        FakeHttpTransport transport = new FakeHttpTransport().respondWith(200, ollamaReply("hola"));
        OllamaService service = new OllamaService(ollamaConfig(), transport);

        assertThat(service.chat("eres util", "di hola")).isEqualTo("hola");

        FakeHttpTransport.Call call = transport.calls.get(0);
        assertThat(call.url()).isEqualTo("http://localhost:11434/api/chat");
        assertThat(call.headers()).isEmpty();
        assertThat(call.body()).contains("\"stream\":false");
    }

    @Test
    void ollamaAlsoUnderstandsTheOlderResponseField() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .respondWith(200, "{\"response\":\"texto antiguo\"}");

        assertThat(new OllamaService(ollamaConfig(), transport).chat("s", "u"))
                .isEqualTo("texto antiguo");
    }

    @Test
    void aSaturatedProviderIsRetried() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .respondWith(429, "{\"error\":\"rate limit\"}")
                .respondWith(200, openAiReply("por fin"));

        assertThat(new OpenAIService(openAiConfig(), transport).chat("s", "u")).isEqualTo("por fin");
        assertThat(transport.calls).hasSize(2);
    }

    @Test
    void aServerErrorIsRetriedUntilTheLimit() {
        FakeHttpTransport transport = new FakeHttpTransport().respondWith(500, "boom");

        assertThatThrownBy(() -> new OpenAIService(openAiConfig(), transport).chat("s", "u"))
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining("500");
        assertThat(transport.calls).hasSize(3);   // intento inicial y dos reintentos
    }

    @Test
    void aClientErrorIsNotRetried() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .respondWith(401, "{\"error\":\"invalid api key\"}");

        assertThatThrownBy(() -> new OpenAIService(openAiConfig(), transport).chat("s", "u"))
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining("401");
        assertThat(transport.calls).hasSize(1);
    }

    @Test
    void aNetworkFailureIsReportedAsUnavailable() {
        FakeHttpTransport transport = new FakeHttpTransport()
                .failWith(new de.omnistreamforce.ai.http.HttpTransport.HttpTransportException(
                        "conexion rechazada", new java.io.IOException()));

        assertThatThrownBy(() -> new OllamaService(ollamaConfig(), transport).chat("s", "u"))
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining("No se pudo contactar");
    }

    @Test
    void anEmptyAnswerIsTreatedAsUnavailable() {
        FakeHttpTransport transport = new FakeHttpTransport().respondWith(200, "{\"choices\":[]}");

        assertThatThrownBy(() -> new OpenAIService(openAiConfig(), transport).chat("s", "u"))
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining("sin contenido");
    }

    @Test
    void proposeSchemaParsesWhatTheModelAnswers() {
        String schemaJson = """
                {"domain":"logistics","fields":[{"name":"shipmentId","type":"string"}],
                 "normalEvents":["ShipmentCreated"],"errorEvents":["ShipmentLost"]}""";
        FakeHttpTransport transport = new FakeHttpTransport()
                .respondWith(200, ollamaReply("```json\\n" + schemaJson + "\\n```"));

        EventSchema schema = new OllamaService(ollamaConfig(), transport)
                .proposeSchema("logistics", "paqueteria urbana");

        assertThat(schema.domain()).isEqualTo("logistics");
        assertThat(schema.eventTypes()).hasSize(2);
        // el contexto del usuario llega al modelo
        assertThat(transport.calls.get(0).body()).contains("paqueteria urbana");
    }

    @Test
    void withoutApiKeyOpenAiIsNotEvenTried() {
        FakeHttpTransport transport = new FakeHttpTransport();
        AIConfig noKey = AIConfig.builder(AIProvider.OPENAI).apiKey(null).build();

        assertThat(new OpenAIService(noKey, transport).isAvailable()).isFalse();
        assertThat(transport.calls).isEmpty();
    }

    @Test
    void availabilityReflectsWhetherTheProviderAnswers() {
        assertThat(new OllamaService(ollamaConfig(),
                new FakeHttpTransport().respondWith(200, ollamaReply("OK"))).isAvailable()).isTrue();

        assertThat(new OllamaService(ollamaConfig(),
                new FakeHttpTransport().respondWith(500, "boom")).isAvailable()).isFalse();
    }

    @Test
    void aDisabledServiceNeverCallsAnything() {
        AIService service = AIServiceFactory.create(AIConfig.disabled());

        assertThat(service.isAvailable()).isFalse();
        assertThat(service.providerName()).isEqualTo("ninguno");
        assertThatThrownBy(() -> service.chat("s", "u"))
                .isInstanceOf(AIUnavailableException.class);
    }

    @Test
    void theFactoryPicksTheProvider() {
        assertThat(AIServiceFactory.create(AIConfig.builder(AIProvider.OPENAI).apiKey("k").build()))
                .isInstanceOf(OpenAIService.class);
        assertThat(AIServiceFactory.create(AIConfig.builder(AIProvider.OLLAMA).build()))
                .isInstanceOf(OllamaService.class);
    }

    @Test
    void configurationCanComeFromTheEnvironment() {
        AIConfig config = AIConfig.fromEnv(name -> switch (name) {
            case "OSF_AI_PROVIDER" -> "openai";
            case "OSF_AI_API_KEY" -> "sk-env";
            case "OSF_AI_MODEL" -> "gpt-4o";
            default -> null;
        });

        assertThat(config.provider()).isEqualTo(AIProvider.OPENAI);
        assertThat(config.apiKey()).isEqualTo("sk-env");
        assertThat(config.model()).isEqualTo("gpt-4o");
        assertThat(config.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(config.enabled()).isTrue();
    }

    @Test
    void defaultsFillTheGaps() {
        AIConfig config = AIConfig.builder(AIProvider.OLLAMA).build();

        assertThat(config.model()).isEqualTo("llama3");
        assertThat(config.baseUrl()).isEqualTo("http://localhost:11434");
        assertThat(config.maxTokens()).isEqualTo(2048);
        assertThat(config.timeoutMs()).isEqualTo(60_000);
    }
}
