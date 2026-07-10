package de.omnistreamforce.serializer;

import de.omnistreamforce.core.Event;

/**
 * Interfaz para serializar eventos a bytes en distintos formatos.
 */
public interface EventSerializer {

    byte[] serialize(Event event);

    Event deserialize(byte[] data);

    String getFormatName();
}