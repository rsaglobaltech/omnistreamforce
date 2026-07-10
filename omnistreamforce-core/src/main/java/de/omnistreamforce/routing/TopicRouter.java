package de.omnistreamforce.routing;

import de.omnistreamforce.core.Event;

/**
 * Mapea un evento a su topic de destino segun la configuracion de routing.
 * Soporta multi-topic routing: cada dominio puede publicar a un topic distinto,
 * y los eventos de error pueden ir a un topic separado.
 */
@FunctionalInterface
public interface TopicRouter {

    /**
     * Resuelve el topic de destino para el evento indicado.
     *
     * @param event evento a enrutar
     * @return nombre del topic de destino
     */
    String route(Event event);
}