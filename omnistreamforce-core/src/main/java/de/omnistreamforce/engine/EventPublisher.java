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
     * Fuerza el envio de todo lo que el publisher tenga pendiente y bloquea hasta que el
     * destino lo confirme. Por defecto no hace nada (publisher sin buffer propio).
     * <p>
     * El relay del outbox depende de esto: solo marca una fila como publicada cuando el
     * lote ha llegado realmente al destino.
     */
    default void flush() {
    }

    /**
     * Cierra el publisher liberando recursos.
     */
    void close();
}