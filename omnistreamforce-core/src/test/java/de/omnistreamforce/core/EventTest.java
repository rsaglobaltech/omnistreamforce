package de.omnistreamforce.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventTest {

    @Test
    void eventRecordHoldsAllFields() {
        Event event = new Event(
                "evt-1",
                EventType.NORMAL.name(),
                "healthcare",
                "omnistreamforce",
                System.currentTimeMillis(),
                "1.0",
                java.util.Map.of("patientId", "p-1"),
                java.util.Map.of("env", "test"),
                "trace-1",
                "corr-1"
        );

        assertThat(event.eventId()).isEqualTo("evt-1");
        assertThat(event.eventType()).isEqualTo("NORMAL");
        assertThat(event.domain()).isEqualTo("healthcare");
        assertThat(event.payload()).containsEntry("patientId", "p-1");
        assertThat(event.metadata()).containsEntry("env", "test");
    }

    @Test
    void eventTypeIsErrorFlag() {
        assertThat(EventType.ERROR.isError()).isTrue();
        assertThat(EventType.NORMAL.isError()).isFalse();
        assertThat(EventType.WARNING.isError()).isFalse();
        assertThat(EventType.INFO.isError()).isFalse();
    }
}