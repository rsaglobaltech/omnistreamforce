package de.omnistreamforce.ai;

import de.omnistreamforce.ai.http.JdkHttpTransport;

/**
 * Crea el servicio segun el proveedor configurado.
 */
public final class AIServiceFactory {

    private AIServiceFactory() {
    }

    public static AIService create(AIConfig config) {
        if (!config.enabled()) {
            return new DisabledAIService();
        }
        return switch (config.provider()) {
            case OPENAI -> new OpenAIService(config, new JdkHttpTransport(config.timeoutMs()));
            case OLLAMA -> new OllamaService(config, new JdkHttpTransport(config.timeoutMs()));
        };
    }

    /**
     * Servicio que no hace nada, para no obligar a comprobar nulos cuando la IA esta apagada.
     */
    public static final class DisabledAIService implements AIService {

        @Override
        public String chat(String systemPrompt, String userPrompt) {
            throw new AIUnavailableException("La IA esta desactivada");
        }

        @Override
        public de.omnistreamforce.core.EventSchema proposeSchema(String domainName, String description) {
            throw new AIUnavailableException("La IA esta desactivada");
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String providerName() {
            return "ninguno";
        }
    }
}
