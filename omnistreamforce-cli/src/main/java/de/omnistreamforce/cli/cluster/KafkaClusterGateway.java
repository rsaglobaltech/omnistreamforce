package de.omnistreamforce.cli.cluster;

import de.omnistreamforce.kafka.ClusterInfo;
import de.omnistreamforce.kafka.KafkaConnectionConfig;
import de.omnistreamforce.kafka.KafkaConnectionManager;
import de.omnistreamforce.kafka.KafkaTopicManager;
import org.apache.kafka.clients.producer.Producer;

import java.util.Map;

/**
 * Implementacion real sobre {@link KafkaConnectionManager} y {@link KafkaTopicManager}.
 */
public class KafkaClusterGateway implements ClusterGateway {

    private final KafkaConnectionManager manager;
    private final KafkaTopicManager topics;

    public KafkaClusterGateway(KafkaConnectionConfig config) {
        this.manager = new KafkaConnectionManager(config);
        this.topics = new KafkaTopicManager(manager.admin());
    }

    @Override
    public ClusterInfo connect() {
        manager.connect();
        return manager.clusterInfo();
    }

    @Override
    public Map<String, Integer> listTopicsWithPartitions() {
        return topics.listTopicsWithPartitions();
    }

    @Override
    public boolean verifyOrCreateTopic(String topic, int partitions, short replicationFactor) {
        return topics.verifyOrCreateTopic(topic, partitions, replicationFactor, true);
    }

    /** El producer ya conectado, para reutilizarlo en la fase de publicacion. */
    public Producer<String, byte[]> producer() {
        return manager.producer();
    }

    @Override
    public void close() {
        manager.close();
    }
}
