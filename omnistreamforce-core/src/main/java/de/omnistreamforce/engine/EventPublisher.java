package de.omnistreamforce.engine;

import de.omnistreamforce.core.Event;

import java.util.List;

/**
 * Interfaz para publicar eventos en el destino correspondiente (Kafka en la Fase 4).
 */
public interface EventPublisher {

    /**
     * Publica un evento al topic indicado de forma asincrona (con callback interno).
     */
    void publish(Event event, String topic);

    /**
     * Publica un lote de eventos al topic indicado.
     */
    default void publishBatch(List<Event> events, String topic) {
        for (Event event : events) {
            publish(event, topic);
        }
    }

    /**
     * Cierra el publisher liberando recursos.
     */
    void close();
}