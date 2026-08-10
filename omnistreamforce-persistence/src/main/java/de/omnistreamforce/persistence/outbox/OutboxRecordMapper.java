package de.omnistreamforce.persistence.outbox;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.serializer.EventSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Convierte un {@link Event} ya ruteado en la fila de outbox correspondiente.
 * <p>
 * Aplica la misma estrategia de clave que el publisher de Kafka, de modo que un evento tenga
 * exactamente la misma clave salga por donde salga.
 */
public final class OutboxRecordMapper {

    /** Clave de metadata donde el motor deja la clave del mensaje ya resuelta. */
    public static final String KEY_METADATA = "kafka.key";
    public static final String EVENT_NAME_METADATA = "eventName";
    public static final String ERROR_TYPE_METADATA = "errorType";

    private final EventSerializer serializer;
    private final KeyStrategy keyStrategy;
    private final String keyField;
    private final AtomicLong roundRobin = new AtomicLong();

    public OutboxRecordMapper(EventSerializer serializer) {
        this(serializer, KeyStrategy.RANDOM, null);
    }

    public OutboxRecordMapper(EventSerializer serializer, KeyStrategy keyStrategy, String keyField) {
        this.serializer = serializer;
        this.keyStrategy = keyStrategy == null ? KeyStrategy.RANDOM : keyStrategy;
        this.keyField = keyField;
    }

    /**
     * Clave del mensaje: la que ya calculo el motor si esta presente; si no, se aplica la
     * estrategia configurada; y como ultimo recurso el eventId, que es estable y por tanto
     * idempotente (a diferencia de un UUID nuevo por intento).
     */
    public String resolveKey(Event event) {
        String precomputed = metadata(event, KEY_METADATA);
        if (precomputed != null && !precomputed.isBlank()) {
            return precomputed;
        }
        if (keyStrategy == KeyStrategy.ENTITY_ID && keyField != null && event.payload() != null) {
            Object value = event.payload().get(keyField);
            if (value != null) {
                return value.toString();
            }
        }
        if (keyStrategy == KeyStrategy.ROUND_ROBIN) {
            return String.valueOf(roundRobin.incrementAndGet());
        }
        return event.eventId();
    }

    public OutboxRecord toRecord(Event event, String topic) {
        return new OutboxRecord(
                event.eventId(),
                topic,
                resolveKey(event),
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
