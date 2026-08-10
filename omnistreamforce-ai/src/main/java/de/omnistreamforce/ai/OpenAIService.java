package de.omnistreamforce.ai;

import com.fasterxml.jackson.databind.JsonNode;
import de.omnistreamforce.ai.http.HttpTransport;
import de.omnistreamforce.ai.http.JdkHttpTransport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementacion sobre la API de chat de OpenAI.
 */
public class OpenAIService extends AbstractChatService {

    public OpenAIService(AIConfig config) {
        this(config, new JdkHttpTransport(config.timeoutMs()));
    }

    public OpenAIService(AIConfig config, HttpTransport transport) {
        super(config, transport);
    }

    @Override
    public String providerName() {
        return "OpenAI";
    }

    @Override
    protected String endpoint() {
        return config.baseUrl() + "/chat/completions";
    }

    @Override
    protected Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        if (config.apiKey() != null && !config.apiKey().isBlank()) {
            headers.put("Authorization", "Bearer " + config.apiKey());
        }
        return headers;
    }

    @Override
    protected String buildRequestBody(String systemPrompt, String userPrompt) {
        return """
                {"model":%s,"temperature":%s,"max_tokens":%d,"messages":[\
                {"role":"system","content":%s},{"role":"user","content":%s}]}"""
                .formatted(jsonString(config.model()), config.temperature(), config.maxTokens(),
                        jsonString(systemPrompt), jsonString(userPrompt));
    }

    @Override
    protected String extractContent(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new AIUnavailableException("Respuesta de OpenAI sin contenido");
            }
            return content.asText();
        } catch (AIUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new AIUnavailableException("Respuesta de OpenAI ilegible", e);
        }
    }

    @Override
    public boolean isAvailable() {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            return false;   // sin clave no hay nada que intentar
        }
        return super.isAvailable();
    }
}
