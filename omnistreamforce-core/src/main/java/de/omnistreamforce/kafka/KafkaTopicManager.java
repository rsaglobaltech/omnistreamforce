package de.omnistreamforce.kafka;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartitionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Gestion de topics: verificacion/creacion y listado con particiones
 * para seleccion interactiva en el CLI.
 */
public class KafkaTopicManager {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicManager.class);
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    private final Admin admin;
    private final Properties consumerProps;

    public KafkaTopicManager(Admin admin, Properties consumerProps) {
        this.admin = admin;
        this.consumerProps = consumerProps;
    }

    public KafkaTopicManager(Admin admin) {
        this(admin, null);
    }

    /**
     * Lista todos los topics disponibles (no internos).
     */
    public Set<String> listTopics() {
        try {
            return admin.listTopics(new ListTopicsOptions().timeoutMs(DEFAULT_TIMEOUT_MS)).names().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Listado de topics interrumpido", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error listando topics", e.getCause());
        }
    }

    /**
     * Lista topics con el numero de particiones de cada uno (para seleccion interactiva).
     */
    public Map<String, Integer> listTopicsWithPartitions() {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (consumerProps != null && consumerProps.containsKey("bootstrap.servers")) {
            try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerPropsWithDeserializers())) {
                Map<String, List<PartitionInfo>> topics = consumer.listTopics();
                topics.forEach((name, partitions) -> result.put(name, partitions == null ? 0 : partitions.size()));
            }
            return result;
        }
        // Fallback usando AdminClient describeTopics
        Set<String> names = listTopics();
        for (String name : names) {
            result.put(name, describePartitions(name));
        }
        return result;
    }

    /**
     * Verifica si un topic existe; si no y {@code autoCreate} es true, lo crea.
     */
    public boolean verifyOrCreateTopic(String topicName, int partitions, short replicationFactor, boolean autoCreate) {
        if (topicExists(topicName)) {
            return true;
        }
        if (!autoCreate) {
            return false;
        }
        return createTopic(topicName, partitions, replicationFactor);
    }

    public boolean topicExists(String topicName) {
        return listTopics().contains(topicName);
    }

    public boolean createTopic(String topicName, int partitions, short replicationFactor) {
        NewTopic newTopic = new NewTopic(topicName, partitions, replicationFactor);
        try {
            CreateTopicsResult result = admin.createTopics(Collections.singleton(newTopic));
            result.all().get();
            log.info("Topic creado: {} ({} particiones, rf {})", topicName, partitions, replicationFactor);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Creacion de topic interrumpida", e);
        } catch (ExecutionException e) {
            if (e.getCause() != null && e.getCause().getClass().getSimpleName().contains("TopicExistsException")) {
                return true;
            }
            throw new RuntimeException("Error creando topic " + topicName, e.getCause());
        }
    }

    /**
     * Detalles de un topic especifico.
     */
    public TopicInfo getTopicInfo(String topicName) {
        try {
            DescribeTopicsResult result = admin.describeTopics(Collections.singleton(topicName));
            TopicDescription desc = result.topicNameValues().get(topicName).get();
            int partitions = desc.partitions().size();
            short rf = partitions > 0 ? (short) desc.partitions().get(0).replicas().size() : 1;
            Map<Integer, Integer> leaderCount = new HashMap<>();
            return new TopicInfo(desc.name(), partitions, rf, desc.isInternal(), leaderCount);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Describe topics interrumpido", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error obteniendo info del topic " + topicName, e.getCause());
        }
    }

    private int describePartitions(String topicName) {
        try {
            DescribeTopicsResult result = admin.describeTopics(Collections.singleton(topicName));
            List<TopicPartitionInfo> partitions = result.topicNameValues().get(topicName).get().partitions();
            return partitions == null ? 0 : partitions.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private Properties consumerPropsWithDeserializers() {
        Properties props = new Properties();
        props.putAll(consumerProps);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        return props;
    }
}