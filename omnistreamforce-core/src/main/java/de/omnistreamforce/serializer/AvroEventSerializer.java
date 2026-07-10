package de.omnistreamforce.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.omnistreamforce.core.Event;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializador Avro. Define un esquema estatico que refleja la estructura de {@link Event},
 * serializando el payload heterogeneo como una cadena JSON dentro del registro Avro.
 * Round-trip completo.
 */
public class AvroEventSerializer implements EventSerializer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Schema SCHEMA = Schema.createRecord("Event", "OmniStreamForce event", "de.omnistreamforce", false,
            java.util.List.of(
                    field("eventId", Schema.create(Schema.Type.STRING)),
                    field("eventType", Schema.create(Schema.Type.STRING)),
                    field("domain", Schema.create(Schema.Type.STRING)),
                    field("source", Schema.create(Schema.Type.STRING)),
                    field("timestamp", Schema.create(Schema.Type.LONG)),
                    field("schemaVersion", Schema.create(Schema.Type.STRING)),
                    field("payload", Schema.create(Schema.Type.STRING)),
                    field("metadata", Schema.createMap(Schema.create(Schema.Type.STRING))),
                    field("traceId", Schema.create(Schema.Type.STRING)),
                    field("correlationId", Schema.create(Schema.Type.STRING))
            ));

    private static Schema.Field field(String name, Schema schema) {
        return new Schema.Field(name, schema, null, null);
    }

    @Override
    public byte[] serialize(Event event) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            GenericRecordBuilder builder = new GenericRecordBuilder(SCHEMA);
            builder.set("eventId", event.eventId());
            builder.set("eventType", event.eventType());
            builder.set("domain", event.domain());
            builder.set("source", event.source());
            builder.set("timestamp", event.timestamp());
            builder.set("schemaVersion", event.schemaVersion());
            builder.set("payload", event.payload() == null ? "{}" : JSON.writeValueAsString(event.payload()));
            builder.set("metadata", event.metadata());
            builder.set("traceId", event.traceId());
            builder.set("correlationId", event.correlationId());
            GenericDatumWriter<GenericRecord> writer = new GenericDatumWriter<>(SCHEMA);
            writer.write(builder.build(), encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Error serializando evento a Avro", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Event deserialize(byte[] data) {
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(data, null);
        try {
            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(SCHEMA);
            GenericRecord record = reader.read(null, decoder);
            Object payloadRaw = record.get("payload");
            String payloadJson = payloadRaw == null ? "{}" : payloadRaw.toString();
            Map<String, Object> payload = JSON.readValue(payloadJson, LinkedHashMap.class);
            Map<String, String> metadata = convertToStringMap(record.get("metadata"));
            return new Event(
                    str(record.get("eventId")),
                    str(record.get("eventType")),
                    str(record.get("domain")),
                    str(record.get("source")),
                    (Long) record.get("timestamp"),
                    str(record.get("schemaVersion")),
                    payload,
                    metadata,
                    str(record.get("traceId")),
                    str(record.get("correlationId"))
            );
        } catch (IOException e) {
            throw new RuntimeException("Error deserializando evento desde Avro", e);
        }
    }

    @Override
    public String getFormatName() {
        return "AVRO";
    }

    public Schema schema() {
        return SCHEMA;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> convertToStringMap(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        Map<Object, Object> source = (Map<Object, Object>) raw;
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((k, v) -> result.put(k == null ? "" : k.toString(), v == null ? "" : v.toString()));
        return result;
    }
}