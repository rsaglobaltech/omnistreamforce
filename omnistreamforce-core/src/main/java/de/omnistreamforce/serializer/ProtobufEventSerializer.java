package de.omnistreamforce.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import de.omnistreamforce.core.Event;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializador Protobuf usando el tipo well-known {@link Struct} para representar el evento.
 * Serializa a binario Protobuf real y reconstruye el {@link Event} en el round-trip.
 */
public class ProtobufEventSerializer implements EventSerializer {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public byte[] serialize(Event event) {
        try {
            String json = JSON.writeValueAsString(event);
            Struct.Builder structBuilder = Struct.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(json, structBuilder);
            return structBuilder.build().toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error serializando evento a Protobuf", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Event deserialize(byte[] data) {
        try {
            Struct struct = Struct.parseFrom(data);
            String json = JsonFormat.printer().print(struct);
            Map<String, Object> map = JSON.readValue(json, LinkedHashMap.class);
            Map<String, Object> payload = map.get("payload") == null
                    ? Map.of() : (Map<String, Object>) map.get("payload");
            Map<String, String> metadata = convertToStringMap(map.get("metadata"));
            Object ts = map.get("timestamp");
            long timestamp = ts instanceof Number ? ((Number) ts).longValue() : 0L;
            return new Event(
                    (String) map.get("eventId"),
                    (String) map.get("eventType"),
                    (String) map.get("domain"),
                    (String) map.get("source"),
                    timestamp,
                    (String) map.get("schemaVersion"),
                    payload,
                    metadata,
                    (String) map.get("traceId"),
                    (String) map.get("correlationId")
            );
        } catch (Exception e) {
            throw new RuntimeException("Error deserializando evento desde Protobuf", e);
        }
    }

    @Override
    public String getFormatName() {
        return "PROTOBUF";
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> convertToStringMap(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        Map<String, Object> source = (Map<String, Object>) raw;
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((k, v) -> result.put(k, v == null ? "" : v.toString()));
        return result;
    }
}