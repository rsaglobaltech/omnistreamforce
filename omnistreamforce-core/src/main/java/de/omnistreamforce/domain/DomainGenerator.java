package de.omnistreamforce.domain;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventType;

import java.util.List;

/**
 * Interfaz/estrategia que cada dominio de negocio implementa para generar eventos.
 */
public interface DomainGenerator {

    String getDomainName();

    List<String> getSupportedEventTypes();

    /**
     * Genera un evento del tipo indicado (normal o de error segun {@link EventType#isError()}).
     *
     * @param eventType tipo de evento soportado por el dominio (nombre logico del evento)
     */
    Event generateEvent(String eventType);

    /**
     * Genera un evento de error aleatorio dentro de los tipos de error del dominio.
     */
    Event generateErrorEvent();
}