package de.omnistreamforce.kafka;

import java.util.List;

/**
 * Metadata del cluster detectada durante la conexion.
 *
 * @param kafkaVersion nivel de {@code metadata.version} finalizado en el cluster (KRaft),
 *                     o {@code "unknown"} si el cluster no lo expone. El AdminClient no
 *                     publica la version exacta del broker.
 * @param controllerId id del broker controlador, o {@code -1} si no se pudo determinar
 */
public record ClusterInfo(
        ClusterType clusterType,
        String clusterId,
        List<String> brokers,
        String kafkaVersion,
        int controllerId
) {
    public ClusterInfo {
        brokers = brokers == null ? List.of() : List.copyOf(brokers);
        kafkaVersion = kafkaVersion == null || kafkaVersion.isBlank() ? "unknown" : kafkaVersion;
    }
}