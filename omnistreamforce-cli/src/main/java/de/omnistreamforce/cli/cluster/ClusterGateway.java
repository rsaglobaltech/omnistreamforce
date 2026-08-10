package de.omnistreamforce.cli.cluster;

import de.omnistreamforce.kafka.ClusterInfo;

import java.util.Map;

/**
 * Lo que el flujo interactivo necesita de un cluster de Kafka.
 * <p>
 * Existe para que la conversacion con el usuario se pueda probar entera sin levantar un broker:
 * los tests inyectan una implementacion de mentira.
 */
public interface ClusterGateway extends AutoCloseable {

    /** Conecta y devuelve la metadata del cluster. */
    ClusterInfo connect();

    /** Topics existentes con su numero de particiones. */
    Map<String, Integer> listTopicsWithPartitions();

    /** Crea el topic si no existe; devuelve true si lo ha creado. */
    boolean verifyOrCreateTopic(String topic, int partitions, short replicationFactor);

    @Override
    void close();
}
