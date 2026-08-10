package de.omnistreamforce.ai;

import java.util.Locale;

/**
 * Configuracion del servicio de IA.
 * <p>
 * La IA es una mejora, nunca un requisito: si no esta configurada o no responde, el generador
 * sigue funcionando con los dominios y esquemas de siempre.
 *
 * @param provider  OPENAI u OLLAMA
 * @param apiKey    solo lo necesita OpenAI; Ollama corre en local sin credenciales
 * @param baseUrl   permite apuntar a un proxy o a una instancia propia
 * @param timeoutMs tiempo maximo de espera de una respuesta del modelo
 */
public record AIConfig(
        boolean enabled,
        AIProvider provider,
        String apiKey,
        String model,
        String baseUrl,
        double temperature,
        int maxTokens,
        long timeoutMs,
        int maxRetries,
        long retryBackoffMs
) {

    public AIConfig {
        provider = provider == null ? AIProvider.OLLAMA : provider;
        model = model == null || model.isBlank() ? provider.defaultModel() : model;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? provider.defaultBaseUrl() : baseUrl;
        temperature = temperature < 0 ? 0 : Math.min(temperature, 2.0);
        maxTokens = maxTokens <= 0 ? 2048 : maxTokens;
        timeoutMs = timeoutMs <= 0 ? 60_000 : timeoutMs;
        maxRetries = Math.max(0, maxRetries);
        retryBackoffMs = Math.max(0, retryBackoffMs);
    }

    public static AIConfig disabled() {
        return new AIConfig(false, AIProvider.OLLAMA, null, null, null, 0.2, 2048, 60_000, 2, 500);
    }

    public static Builder builder(AIProvider provider) {
        return new Builder(provider);
    }

    /**
     * Configuracion desde el entorno: OSF_AI_ENABLED, OSF_AI_PROVIDER, OSF_AI_API_KEY,
     * OSF_AI_MODEL y OSF_AI_BASE_URL.
     */
    public static AIConfig fromEnv() {
        return fromEnv(System::getenv);
    }

    static AIConfig fromEnv(java.util.function.Function<String, String> env) {
        String enabled = env.apply("OSF_AI_ENABLED");
        String provider = env.apply("OSF_AI_PROVIDER");
        AIProvider resolved = provider == null || provider.isBlank()
                ? AIProvider.OLLAMA
                : AIProvider.valueOf(provider.trim().toUpperCase(Locale.ROOT));

        return builder(resolved)
                .enabled(enabled == null || enabled.isBlank() || Boolean.parseBoolean(enabled.trim()))
                .apiKey(env.apply("OSF_AI_API_KEY"))
                .model(env.apply("OSF_AI_MODEL"))
                .baseUrl(env.apply("OSF_AI_BASE_URL"))
                .build();
    }

    public static final class Builder {
        private final AIProvider provider;
        private boolean enabled = true;
        private String apiKey;
        private String model;
        private String baseUrl;
        private double temperature = 0.2;
        private int maxTokens = 2048;
        private long timeoutMs = 60_000;
        private int maxRetries = 2;
        private long retryBackoffMs = 500;

        private Builder(AIProvider provider) {
            this.provider = provider;
        }

        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder model(String v) { this.model = v; return this; }
        public Builder baseUrl(String v) { this.baseUrl = v; return this; }
        public Builder temperature(double v) { this.temperature = v; return this; }
        public Builder maxTokens(int v) { this.maxTokens = v; return this; }
        public Builder timeoutMs(long v) { this.timeoutMs = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }
        public Builder retryBackoffMs(long v) { this.retryBackoffMs = v; return this; }

        public AIConfig build() {
            return new AIConfig(enabled, provider, apiKey, model, baseUrl, temperature,
                    maxTokens, timeoutMs, maxRetries, retryBackoffMs);
        }
    }
}
