package de.omnistreamforce.ai;

/**
 * Proveedores de modelo soportados.
 */
public enum AIProvider {

    /** API de OpenAI; requiere clave. */
    OPENAI("https://api.openai.com/v1", "gpt-4o-mini"),

    /** Instancia local de Ollama; sin credenciales y sin coste. */
    OLLAMA("http://localhost:11434", "llama3");

    private final String defaultBaseUrl;
    private final String defaultModel;

    AIProvider(String defaultBaseUrl, String defaultModel) {
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }
}
