package de.omnistreamforce.ai;

import com.fasterxml.jackson.databind.JsonNode;
import de.omnistreamforce.ai.http.HttpTransport;
import de.omnistreamforce.ai.http.JdkHttpTransport;

import java.util.Map;

/**
 * Implementacion sobre una instancia local de Ollama: sin credenciales y sin coste por peticion.
 */
public class OllamaService extends AbstractChatService {

    public OllamaService(AIConfig config) {
        this(config, new JdkHttpTransport(config.timeoutMs()));
    }

    public OllamaService(AIConfig config, HttpTransport transport) {
        super(config, transport);
    }

    @Override
    public String providerName() {
        return "Ollama";
    }

    @Override
    protected String endpoint() {
        return config.baseUrl() + "/api/chat";
    }

    @Override
    protected Map<String, String> headers() {
        return Map.of();
    }

    @Override
    protected String buildRequestBody(String systemPrompt, String userPrompt) {
        // stream=false para recibir la respuesta completa en un solo JSON
        return """
                {"model":%s,"stream":false,"options":{"temperature":%s,"num_predict":%d},\
                "messages":[{"role":"system","content":%s},{"role":"user","content":%s}]}"""
                .formatted(jsonString(config.model()), config.temperature(), config.maxTokens(),
                        jsonString(systemPrompt), jsonString(userPrompt));
    }

    @Override
    protected String extractContent(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode content = root.path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                // algunas versiones antiguas devuelven "response" en vez de "message"
                content = root.path("response");
            }
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new AIUnavailableException("Respuesta de Ollama sin contenido");
            }
            return content.asText();
        } catch (AIUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AIUnavailableException("Respuesta de Ollama ilegible", e);
        }
    }
}
