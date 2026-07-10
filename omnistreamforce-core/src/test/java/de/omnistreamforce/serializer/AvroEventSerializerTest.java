package de.omnistreamforce.serializer;

import de.omnistreamforce.core.Event;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AvroEventSerializerTest {

    private final AvroEventSerializer serializer = new AvroEventSerializer();

    @Test
    void roundTripsEvent() {
        Event event = sampleEvent();
        byte[] data = serializer.serialize(event);
        Event back = serializer.deserialize(data);
        assertThat(back.eventId()).isEqualTo(event.eventId());
        assertThat(back.domain()).isEqualTo("ecommerce");
        assertThat(back.timestamp()).isEqualTo(event.timestamp());
        assertThat(back.payload()).containsEntry("orderId", "ORD-123");
        assertThat(back.metadata()).containsEntry("eventName", "OrderCreated");
        assertThat(back.traceId()).isEqualTo("trace-1");
    }

    @Test
    void avroFormatName() {
        assertThat(serializer.getFormatName()).isEqualTo("AVRO");
    }

    static Event sampleEvent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", "ORD-123");
        payload.put("products", List.of(Map.of("id", "SKU-1")));
        return new Event("id-avro", "NORMAL", "ecommerce", "omnistreamforce",
                1700000000000L, "1.0", payload,
                Map.of("eventName", "OrderCreated"), "trace-1", "corr-1");
    }
}