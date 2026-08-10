package de.omnistreamforce.persistence.outbox;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.serializer.EventSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Convierte un {@link Event} ya ruteado en la fila de outbox correspondiente.
 */
public final class OutboxRecordMapper {

    /** Clave de metadata donde el motor deja la clave del mensaje ya resuelta. */
    public static final String KEY_METADATA = "kafka.key";
    public static final String EVENT_NAME_METADATA = "eventName";
    public static final String ERROR_TYPE_METADATA = "errorType";

    private final EventSerializer serializer;

    public OutboxRecordMapper(EventSerializer serializer) {
        this.serializer = serializer;
    }

    public OutboxRecord toRecord(Event event, String topic) {
        return new OutboxRecord(
                event.eventId(),
                topic,
                aggregateId(event),
                eventName(event),
                new String(serializer.serialize(event), StandardCharsets.UTF_8),
                event.domain(),
                event.traceId());
    }

    /**
     * Clave del mensaje: la que ya calculo el motor, si no el eventId. Que sea la misma da igual
     * que el evento salga directo a Kafka o pase por el outbox y lo republique el relay.
     */
    public static String aggregateId(Event event) {
        String key = metadata(event, KEY_METADATA);
        return key == null || key.isBlank() ? event.eventId() : key;
    }

    /** Nombre logico del evento: los generadores lo dejan en metadata, no en {@code eventType}. */
    public static String eventName(Event event) {
        String name = metadata(event, EVENT_NAME_METADATA);
        if (name == null || name.isBlank()) {
            name = metadata(event, ERROR_TYPE_METADATA);
        }
        return name == null || name.isBlank() ? event.eventType() : name;
    }

    public static String metadata(Event event, String key) {
        Map<String, String> metadata = event.metadata();
        return metadata == null ? null : metadata.get(key);
    }
}
