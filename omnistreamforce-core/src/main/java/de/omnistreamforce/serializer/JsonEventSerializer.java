package de.omnistreamforce.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.omnistreamforce.core.Event;

/**
 * Serializador JSON basico usando Jackson. Se ampliara en la Fase 5
 * (pretty printing configurable, timestamp ISO-8601, etc.).
 */
public class JsonEventSerializer implements EventSerializer {

    private final ObjectMapper mapper;

    public JsonEventSerializer() {
        this(false);
    }

    public JsonEventSerializer(boolean prettyPrint) {
        this.mapper = new ObjectMapper();
        if (prettyPrint) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
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
}