package de.omnistreamforce.serializer;

import de.omnistreamforce.core.Event;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonEventSerializerTest {

    private final JsonEventSerializer serializer = new JsonEventSerializer();

    @Test
    void roundTripsEvent() {
        Event event = sampleEvent();
        byte[] data = serializer.serialize(event);
        Event back = serializer.deserialize(data);
        assertThat(back).isEqualTo(event);
    }

    @Test
    void prettyPrintContainsNewlines() {
        JsonEventSerializer pretty = new JsonEventSerializer(true);
        String json = new String(pretty.serialize(sampleEvent()));
        assertThat(json).contains("\n");
    }

    @Test
    void isoTimestampRoundTrips() {
        JsonEventSerializer iso = new JsonEventSerializer(false, true);
        Event event = sampleEvent();
        byte[] data = iso.serialize(event);
        String json = new String(data);
        assertThat(json).contains("Z");
        Event back = iso.deserialize(data);
        assertThat(back.timestamp()).isEqualTo(event.timestamp());
        assertThat(back.eventId()).isEqualTo(event.eventId());
    }

    static Event sampleEvent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", "ORD-123");
        payload.put("products", List.of(Map.of("id", "SKU-1")));
        return new Event("id-1", "NORMAL", "ecommerce", "omnistreamforce",
                1700000000000L, "1.0", payload,
                Map.of("eventName", "OrderCreated"), "trace-1", "corr-1");
    }
}