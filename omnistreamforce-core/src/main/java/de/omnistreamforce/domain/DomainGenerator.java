package de.omnistreamforce.domain;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventSchema;

import java.util.List;

/**
 * Interfaz/estrategia que cada dominio de negocio implementa para generar eventos.
 */
public interface DomainGenerator {

    String getDomainName();

    /**
     * Nombres logicos de los tipos de evento normales que el dominio puede generar.
     */
    List<String> getSupportedEventTypes();

    /**
     * Nombres logicos de los tipos de evento de error que el dominio puede generar.
     */
    List<String> getErrorTypes();

    /**
     * Esquema completo del dominio (campos comunes, tipos de evento y especificacion de errores).
     */
    EventSchema getSchema();

    /**
     * Genera un evento del tipo indicado (nombre logico del evento, normal o de error).
     */
    Event generateEvent(String eventType);

    /**
     * Genera un evento de error aleatorio dentro de los tipos de error del dominio.
     */
    Event generateErrorEvent();
}