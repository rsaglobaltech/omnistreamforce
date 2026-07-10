package de.omnistreamforce.kafka;

import java.util.Map;

/**
 * Informacion de un topic del cluster.
 */
public record TopicInfo(
        String name,
        int partitions,
        short replicationFactor,
        boolean internal,
        Map<Integer, Integer> partitionLeaderCount
) {
}