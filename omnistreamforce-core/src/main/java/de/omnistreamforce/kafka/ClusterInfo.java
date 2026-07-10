package de.omnistreamforce.kafka;

import java.util.List;

/**
 * Metadata del cluster detectada durante la conexion.
 */
public record ClusterInfo(
        ClusterType clusterType,
        String clusterId,
        List<String> brokers,
        String kafkaVersion
) {
}