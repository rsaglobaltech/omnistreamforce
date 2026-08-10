package de.omnistreamforce.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.FeatureMetadata;
import org.apache.kafka.clients.admin.FinalizedVersionRange;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.Node;
import org.apache.kafka.clients.producer.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Gestiona la conexion y configuracion del producer de Kafka.
 * <p>
 * Construye un {@link KafkaProducer}&lt;String, byte[]&gt; y un {@link AdminClient} a partir
 * de una {@link KafkaConnectionConfig}. {@code connect()} valida la conexion listando topics
 * del cluster y {@code getClusterInfo()} retorna metadata del cluster.
 */
public class KafkaConnectionManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaConnectionManager.class);

    private final KafkaConnectionConfig config;
    private final Producer<String, byte[]> producer;
    private final Admin admin;
    private final ClusterInfo clusterInfo;

    public KafkaConnectionManager(KafkaConnectionConfig config) {
        this.config = config;
        Properties producerProps = config.toProducerProps();
        Properties adminProps = config.toAdminProps();
        this.producer = new KafkaProducer<>(producerProps);
        this.admin = AdminClient.create(adminProps);
        this.clusterInfo = describeCluster();
        log.info("Conectado a cluster Kafka {} ({}) con {} broker(s)",
                clusterInfo.clusterId(), clusterInfo.clusterType(), clusterInfo.brokers().size());
    }

    /**
     * Valida la conexion listando los topics del cluster.
     */
    public Set<String> connect() {
        try {
            return admin.listTopics(new ListTopicsOptions().timeoutMs(10000)).names().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Conexion interrumpida listando topics", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("No se pudo validar la conexion al cluster Kafka", e.getCause());
        }
    }

    /**
     * Describe el cluster (brokers, version de metadata, id y controlador).
     */
    private ClusterInfo describeCluster() {
        try {
            DescribeClusterResult result = admin.describeCluster();
            String clusterId = result.clusterId().get();
            List<String> brokers = new ArrayList<>();
            for (Node node : result.nodes().get()) {
                brokers.add(node.host() + ":" + node.port());
            }
            Node controller = result.controller().get();
            return new ClusterInfo(config.clusterType(), clusterId, brokers,
                    resolveKafkaVersion(), controller == null ? -1 : controller.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("DescribeCluster interrumpido", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("No se pudo describir el cluster", e.getCause());
        }
    }

    /**
     * El AdminClient no expone la version del broker; lo mas cercano es el nivel de
     * {@code metadata.version} finalizado en el cluster (KRaft). Si no esta disponible
     * (cluster con ZooKeeper o broker antiguo) se devuelve "unknown" en vez de null.
     */
    private String resolveKafkaVersion() {
        try {
            FeatureMetadata features = admin.describeFeatures().featureMetadata().get();
            FinalizedVersionRange metadataVersion = features.finalizedFeatures().get("metadata.version");
            if (metadataVersion != null) {
                return "metadata.version " + metadataVersion.maxVersionLevel();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | RuntimeException e) {
            log.debug("No se pudo determinar la version del cluster: {}", e.getMessage());
        }
        return "unknown";
    }

    public Producer<String, byte[]> producer() {
        return producer;
    }

    public Admin admin() {
        return admin;
    }

    public KafkaConnectionConfig config() {
        return config;
    }

    public ClusterInfo clusterInfo() {
        return clusterInfo;
    }

    /**
     * Graceful shutdown del producer y el admin.
     */
    public void close() {
        try {
            producer.flush();
        } catch (RuntimeException e) {
            log.warn("Error durante flush del producer", e);
        }
        producer.close();
        admin.close();
        log.info("Conexion a Kafka cerrada");
    }
}