package de.omnistreamforce.kafka;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.engine.EventPublisher;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.serializer.EventSerializer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Publicador de eventos a Kafka usando un {@link Producer} existente.
 * <p>
 * Soporta publicacion asincrona con callbacks, estrategia de clave (RANDOM/ENTITY_ID/ROUND_ROBIN),
 * metricas por topic y globales, y retry configurable.
 */
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final Producer<String, byte[]> producer;
    private final EventSerializer serializer;
    private final KeyStrategy keyStrategy;
    private final String keyField;
    private final int maxRetries;
    private final long retryBackoffMs;
    private final Metrics metrics = new Metrics();
    private final AtomicLong roundRobin = new AtomicLong();

    public KafkaEventPublisher(Producer<String, byte[]> producer, EventSerializer serializer,
                               KeyStrategy keyStrategy, String keyField,
                               int maxRetries, long retryBackoffMs) {
        this.producer = producer;
        this.serializer = serializer;
        this.keyStrategy = keyStrategy == null ? KeyStrategy.RANDOM : keyStrategy;
        this.keyField = keyField;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
    }

    public KafkaEventPublisher(Producer<String, byte[]> producer, EventSerializer serializer,
                               KeyStrategy keyStrategy, String keyField) {
        this(producer, serializer, keyStrategy, keyField, 0, 0);
    }

    @Override
    public void publish(Event event, String topic) {
        String key = resolveKey(event);
        byte[] value = serializer == null ? null : serializer.serialize(event);
        metrics.recordSent(topic);
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, value);
        long startNanos = System.nanoTime();
        try {
            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    metrics.recordAck(topic, (System.nanoTime() - startNanos) / 1_000_000.0);
                    if (log.isDebugEnabled()) {
                        log.debug("Evento publicado a {}-p{}@{}", metadata.topic(),
                                metadata.partition(), metadata.offset());
                    }
                } else {
                    metrics.recordFailure(topic);
                    log.error("Fallo publicando evento a topic {}: {}", topic, exception.getMessage());
                    if (maxRetries > 0) {
                        retry(record, topic, 0);
                    }
                }
            });
        } catch (RuntimeException e) {
            metrics.recordFailure(topic);
            log.error("Excepcion enviando a topic {}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void publishBatch(List<Event> events, String topic) {
        for (Event event : events) {
            publish(event, topic);
        }
    }

    private void retry(ProducerRecord<String, byte[]> record, String topic, int attempt) {
        if (attempt >= maxRetries) {
            return;
        }
        try {
            if (retryBackoffMs > 0) {
                Thread.sleep(retryBackoffMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        metrics.recordRetry(topic);
        try {
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    retry(record, topic, attempt + 1);
                } else {
                    metrics.recordAck(topic, 0.0);
                }
            });
        } catch (RuntimeException e) {
            metrics.recordFailure(topic);
        }
    }

    private String resolveKey(Event event) {
        if (keyStrategy == KeyStrategy.ENTITY_ID && keyField != null) {
            Object field = event.payload().get(keyField);
            if (field != null) {
                return field.toString();
            }
        } else if (keyStrategy == KeyStrategy.ROUND_ROBIN) {
            return String.valueOf(roundRobin.incrementAndGet());
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public void close() {
        producer.flush();
        producer.close();
    }

    public Metrics metrics() {
        return metrics;
    }

    public static class Metrics {
        private final AtomicLong totalSent = new AtomicLong();
        private final AtomicLong totalAcknowledged = new AtomicLong();
        private final AtomicLong totalFailed = new AtomicLong();
        private final AtomicLong totalRetries = new AtomicLong();
        private final DoubleAdder latencyMs = new DoubleAdder();
        private final AtomicLong latencyCount = new AtomicLong();

        void recordSent(String topic) {
            totalSent.incrementAndGet();
        }

        void recordAck(String topic, double latencyMs2) {
            totalAcknowledged.incrementAndGet();
            latencyMs.add(latencyMs2);
            latencyCount.incrementAndGet();
        }

        void recordFailure(String topic) {
            totalFailed.incrementAndGet();
        }

        void recordRetry(String topic) {
            totalRetries.incrementAndGet();
        }

        public long totalSent() {
            return totalSent.get();
        }

        public long totalAcknowledged() {
            return totalAcknowledged.get();
        }

        public long totalFailed() {
            return totalFailed.get();
        }

        public long totalRetries() {
            return totalRetries.get();
        }

        public double avgLatencyMs() {
            long c = latencyCount.get();
            return c > 0 ? latencyMs.sum() / c : 0.0;
        }
    }
}