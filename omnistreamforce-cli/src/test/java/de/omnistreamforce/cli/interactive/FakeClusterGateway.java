package de.omnistreamforce.cli.interactive;

import de.omnistreamforce.cli.cluster.ClusterGateway;
import de.omnistreamforce.kafka.ClusterInfo;
import de.omnistreamforce.kafka.ClusterType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cluster de mentira: permite ejercitar la sesion interactiva completa sin broker.
 */
class FakeClusterGateway implements ClusterGateway {

    final Map<String, Integer> topics = new LinkedHashMap<>();
    final List<String> createdTopics = new ArrayList<>();
    boolean connected;
    boolean closed;
    RuntimeException connectFailure;

    FakeClusterGateway(Map<String, Integer> initialTopics) {
        topics.putAll(initialTopics);
    }

    @Override
    public ClusterInfo connect() {
        if (connectFailure != null) {
            throw connectFailure;
        }
        connected = true;
        return new ClusterInfo(ClusterType.LOCAL, "fake-cluster",
                List.of("localhost:9092"), "metadata.version 20", 1);
    }

    @Override
    public Map<String, Integer> listTopicsWithPartitions() {
        return topics;
    }

    @Override
    public boolean verifyOrCreateTopic(String topic, int partitions, short replicationFactor) {
        createdTopics.add(topic);
        topics.put(topic, partitions);
        return true;
    }

    @Override
    public void close() {
        closed = true;
    }
}
