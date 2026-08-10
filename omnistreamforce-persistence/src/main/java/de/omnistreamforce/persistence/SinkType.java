package de.omnistreamforce.persistence;

/**
 * Destino de los eventos generados.
 */
public enum SinkType {

    /** Publicacion directa a Kafka: el comportamiento historico. */
    KAFKA,

    /** Solo base de datos: fila de negocio + outbox. Los eventos salen via relay o CDC. */
    DB_OUTBOX,

    /** Ambos a la vez, para comparar el camino directo con el del outbox. */
    DUAL
}
