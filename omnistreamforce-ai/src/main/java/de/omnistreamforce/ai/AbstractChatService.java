package de.omnistreamforce.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.omnistreamforce.ai.http.HttpTransport;
import de.omnistreamforce.ai.parser.SchemaParser;
import de.omnistreamforce.ai.prompts.PromptTemplates;
import de.omnistreamforce.core.EventSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Base comun de los servicios de chat: reintentos, disponibilidad y propuesta de esquema.
 * Lo especifico de cada proveedor es el cuerpo de la peticion y como extraer el texto.
 */
abstract class AbstractChatService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(AbstractChatService.class);

    protected final AIConfig config;
    protected final HttpTransport transport;
    protected final ObjectMapper mapper = new ObjectMapper();
    private final SchemaParser schemaParser = new SchemaParser();

    protected AbstractChatService(AIConfig config, HttpTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    protected abstract String endpoint();

    protected abstract String buildRequestBody(String systemPrompt, String userPrompt);

    protected abstract String extractContent(String responseBody);

    protected abstract Map<String, String> headers();

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (!config.enabled()) {
            throw new AIUnavailableException("La IA esta desactivada en la configuracion");
        }
        String body = buildRequestBody(systemPrompt, userPrompt);
        long backoff = config.retryBackoffMs();
        RuntimeException last = null;

        for (int attempt = 0; attempt <= config.maxRetries(); attempt++) {
            try {
                HttpTransport.Response response = transport.post(endpoint(), body, headers());
                if (response.isSuccess()) {
                    return extractContent(response.body());
                }
                last = new AIUnavailableException(providerName() + " respondio "
                        + response.status() + ": " + abbreviate(response.body()));
                if (!response.isRetryable()) {
                    throw last;
                }
                // 429 o 5xx: el modelo esta saturado, merece la pena esperar
            } catch (HttpTransport.HttpTransportException e) {
                last = new AIUnavailableException("No se pudo contactar con " + providerName(), e);
            }
            if (attempt < config.maxRetries()) {
                sleep(backoff);
                backoff = Math.min(backoff * 2, 10_000);
            }
        }
        throw last == null ? new AIUnavailableException("Sin respuesta de " + providerName()) : last;
    }

    @Override
    public EventSchema proposeSchema(String domainName, String description) {
        String response = chat(PromptTemplates.SCHEMA_SYSTEM,
                PromptTemplates.schemaProposal(domainName, description));
        return schemaParser.parse(response, domainName);
    }

    @Override
    public boolean isAvailable() {
        if (!config.enabled()) {
            return false;
        }
        try {
            // una pregunta minima basta para saber si el proveedor responde
            chat("Responde solo con OK.", "ping");
            return true;
        } catch (RuntimeException e) {
            log.debug("{} no disponible: {}", providerName(), e.getMessage());
            return false;
        }
    }

    protected String jsonString(String value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el prompt", e);
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
