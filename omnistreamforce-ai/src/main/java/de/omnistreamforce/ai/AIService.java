package de.omnistreamforce.ai;

import de.omnistreamforce.core.EventSchema;

/**
 * Acceso a un modelo de lenguaje.
 * <p>
 * Ninguna funcionalidad del generador depende de esto: si {@link #isAvailable()} es false, quien
 * llama sigue con los dominios registrados de siempre.
 */
public interface AIService extends AutoCloseable {

    /** Una peticion de chat con instruccion de sistema; devuelve el texto de la respuesta. */
    String chat(String systemPrompt, String userPrompt);

    /** Propone un esquema de eventos para un dominio descrito en lenguaje natural. */
    EventSchema proposeSchema(String domainName, String description);

    /** true si el proveedor esta configurado y responde. */
    boolean isAvailable();

    String providerName();

    @Override
    default void close() {
    }
}
