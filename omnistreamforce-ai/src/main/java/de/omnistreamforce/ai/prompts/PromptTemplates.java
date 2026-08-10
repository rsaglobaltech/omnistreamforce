package de.omnistreamforce.ai.prompts;

/**
 * Plantillas de prompt. Se piden respuestas en JSON estricto porque lo que vuelve se parsea a
 * {@link de.omnistreamforce.core.EventSchema}.
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    public static final String SCHEMA_SYSTEM = """
            Eres un arquitecto de datos especializado en Apache Kafka. Respondes SIEMPRE con un
            unico objeto JSON valido, sin texto alrededor y sin bloques de codigo markdown.
            No inventes claves fuera del esquema pedido.""";

    /**
     * Se piden nombres de campo en camelCase y tipos de la lista cerrada que el generador sabe
     * traducir, para que la respuesta encaje sin conversiones raras.
     */
    public static final String SCHEMA_PROPOSAL = """
            Propon un esquema de eventos Kafka para el dominio de negocio '%s'.
            %s

            Requisitos:
            - Entre 4 y 6 tipos de evento normales y entre 2 y 4 de error.
            - Entre 6 y 12 campos comunes, con nombres en camelCase.
            - El tipo de cada campo debe ser uno de: string, int, double, boolean, enum, array, object.
            - Si el tipo es enum, incluye la lista "enumValues".
            - Los eventos de error deben representar fallos reales del dominio, no ruido.

            Responde exactamente con esta forma:
            {
              "domain": "%s",
              "name": "NombreDelEsquema",
              "description": "una frase",
              "fields": [
                {"name":"campo","type":"string","required":true,"description":"para que sirve",
                 "enumValues":["A","B"]}
              ],
              "normalEvents": [
                {"name":"EventoNormal","description":"cuando ocurre"}
              ],
              "errorEvents": [
                {"name":"EventoDeError","description":"que ha fallado"}
              ],
              "errorRate": 0.1
            }""";

    public static final String DATA_GENERATION = """
            Genera datos de ejemplo realistas para un evento '%s' del dominio '%s'.
            Campos: %s.
            Responde solo con un objeto JSON con los valores, sin texto alrededor.""";

    public static final String DOMAIN_DISCOVERY = """
            Para el dominio '%s', describe 5 entidades clave y su relacion, y sugiere nombres de
            topic de Kafka apropiados. Responde en JSON con las claves "entities" y "topics".""";

    public static String schemaProposal(String domain, String description) {
        String detail = description == null || description.isBlank()
                ? "" : "Contexto adicional: " + description;
        return SCHEMA_PROPOSAL.formatted(domain, detail, domain);
    }

    public static String dataGeneration(String eventType, String domain, String fields) {
        return DATA_GENERATION.formatted(eventType, domain, fields);
    }

    public static String domainDiscovery(String domain) {
        return DOMAIN_DISCOVERY.formatted(domain);
    }
}
