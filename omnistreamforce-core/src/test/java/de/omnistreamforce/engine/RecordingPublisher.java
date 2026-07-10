package de.omnistreamforce.engine;

import de.omnistreamforce.core.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * EventPublisher de prueba que captura los eventos por topic para verificacion.
 * Thread-safe.
 */
public class RecordingPublisher implements EventPublisher {

    private final Map<String, List<Event>> eventsByTopic = new ConcurrentHashMap<>();
    private final AtomicLong totalPublished = new AtomicLong();
    private volatile boolean closed = false;

    @Override
    public void publish(Event event, String topic) {
        eventsByTopic.computeIfAbsent(topic, k -> Collections.synchronizedList(new ArrayList<>())).add(event);
        totalPublished.incrementAndGet();
    }

    @Override
    public void close() {
        closed = true;
    }

    public Map<String, List<Event>> eventsByTopic() {
        return eventsByTopic;
    }

    public List<Event> eventsForTopic(String topic) {
        return eventsByTopic.getOrDefault(topic, List.of());
    }

    public long totalPublished() {
        return totalPublished.get();
    }

    public int distinctTopics() {
        return eventsByTopic.size();
    }

    public boolean isClosed() {
        return closed;
    }
}