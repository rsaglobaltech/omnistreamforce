package de.omnistreamforce.serializer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.omnistreamforce.core.Event;

import java.io.IOException;
import java.time.Instant;

/**
 * Serializador JSON usando Jackson.
 * <p>
 * Soporta pretty printing configurable y timestamp en epoch (long) o ISO-8601 (configurable).
 * El round-trip funciona en ambos modos (deserialize acepta ISO y epoch).
 */
public class JsonEventSerializer implements EventSerializer {

    private final ObjectMapper mapper;

    public JsonEventSerializer() {
        this(false, false);
    }

    public JsonEventSerializer(boolean prettyPrint) {
        this(prettyPrint, false);
    }

    public JsonEventSerializer(boolean prettyPrint, boolean isoTimestamp) {
        this.mapper = new ObjectMapper();
        this.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        if (prettyPrint) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
        if (isoTimestamp) {
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, new IsoTimestampSerializer());
            module.addSerializer(Long.TYPE, new IsoTimestampSerializer());
            module.addDeserializer(Long.class, new LenientLongDeserializer(Long.class));
            module.addDeserializer(Long.TYPE, new LenientLongDeserializer(Long.TYPE));
            mapper.registerModule(module);
        }
    }

    @Override
    public byte[] serialize(Event event) {
        try {
            return mapper.writeValueAsBytes(event);
        } catch (Exception e) {
            throw new RuntimeException("Error serializando evento a JSON", e);
        }
    }

    @Override
    public Event deserialize(byte[] data) {
        try {
            return mapper.readValue(data, Event.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializando evento desde JSON", e);
        }
    }

    @Override
    public String getFormatName() {
        return "JSON";
    }

    private static final class IsoTimestampSerializer extends StdSerializer<Long> {
        IsoTimestampSerializer() {
            super(Long.class);
        }

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(Instant.ofEpochMilli(value).toString());
            }
        }
    }

    private static final class LenientLongDeserializer extends StdDeserializer<Long> {
        LenientLongDeserializer(Class<?> vc) {
            super(Long.class);
        }

        @Override
        public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getValueAsString();
            if (text == null || text.isBlank()) {
                return 0L;
            }
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return Instant.parse(text).toEpochMilli();
            }
        }
    }
}