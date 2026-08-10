package de.omnistreamforce.persistence.outbox;

/**
 * Fila de la tabla outbox. Los cinco primeros campos son los que el SMT
 * {@code io.debezium.transforms.outbox.EventRouter} espera por defecto.
 *
 * @param id            identificador del evento; da idempotencia y viaja como header {@code id}
 * @param aggregateType topic de destino ya resuelto por el TopicRouter
 * @param aggregateId   clave del mensaje
 * @param type          nombre logico del evento ({@code eventName} o {@code errorType})
 * @param payload       evento serializado completo
 */
public record OutboxRecord(
        String id,
        String aggregateType,
        String aggregateId,
        String type,
        String payload,
        String domain,
        String traceId
) {
}
