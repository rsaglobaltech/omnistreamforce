package de.omnistreamforce.kafka;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.engine.EventPublisher;
import de.omnistreamforce.engine.KeyStrategy;
import de.omnistreamforce.engine.TopicStats;
import de.omnistreamforce.serializer.EventSerializer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    /** Clave de metadata donde el motor deja la clave ya resuelta por su KeyStrategy. */
    static final String PRECOMPUTED_KEY_METADATA = "kafka.key";

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

    /**
     * Clave del record. Si el motor ya decidio la clave (la deja en {@code metadata["kafka.key"]}
     * al aplicar la KeyStrategy configurada) se respeta, de modo que la clave sea la misma tanto
     * si el evento va directo a Kafka como si pasa por el outbox y lo republica el relay.
     * Sin esa metadata el comportamiento es el de siempre.
     */
    private String resolveKey(Event event) {
        Map<String, String> metadata = event.metadata();
        if (metadata != null) {
            String precomputed = metadata.get(PRECOMPUTED_KEY_METADATA);
            if (precomputed != null && !precomputed.isBlank()) {
                return precomputed;
            }
        }
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
    public void flush() {
        producer.flush();
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
        private final Map<String, TopicAccumulator> perTopic = new ConcurrentHashMap<>();

        void recordSent(String topic) {
            totalSent.incrementAndGet();
            accumulator(topic).recordSent();
        }

        void recordAck(String topic, double latencyMs2) {
            totalAcknowledged.incrementAndGet();
            latencyMs.add(latencyMs2);
            latencyCount.incrementAndGet();
            accumulator(topic).recordAck(latencyMs2);
        }

        void recordFailure(String topic) {
            totalFailed.incrementAndGet();
            accumulator(topic).recordFailure();
        }

        void recordRetry(String topic) {
            totalRetries.incrementAndGet();
            accumulator(topic).recordRetry();
        }

        private TopicAccumulator accumulator(String topic) {
            return perTopic.computeIfAbsent(topic, k -> new TopicAccumulator());
        }

        /**
         * Metricas desglosadas por topic, con la latencia media real medida en el callback
         * del producer (envio -> acuse del broker).
         */
        public Map<String, TopicStats> perTopicStats() {
            Map<String, TopicStats> snapshot = new LinkedHashMap<>();
            perTopic.forEach((topic, accumulator) -> snapshot.put(topic, accumulator.snapshot()));
            return snapshot;
        }

        /**
         * Reintentos acumulados para un topic concreto.
         */
        public long retriesFor(String topic) {
            TopicAccumulator accumulator = perTopic.get(topic);
            return accumulator == null ? 0L : accumulator.retries.get();
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

        /**
         * Acumulador mutable y thread-safe por topic: los callbacks del producer
         * llegan desde el hilo de I/O de Kafka, no desde el hilo que publica.
         */
        private static final class TopicAccumulator {
            private final AtomicLong sent = new AtomicLong();
            private final AtomicLong acknowledged = new AtomicLong();
            private final AtomicLong failed = new AtomicLong();
            private final AtomicLong retries = new AtomicLong();
            private final DoubleAdder latencySum = new DoubleAdder();
            private final AtomicLong latencyCount = new AtomicLong();

            void recordSent() {
                sent.incrementAndGet();
            }

            void recordAck(double latency) {
                acknowledged.incrementAndGet();
                if (latency > 0) {
                    latencySum.add(latency);
                    latencyCount.incrementAndGet();
                }
            }

            void recordFailure() {
                failed.incrementAndGet();
            }

            void recordRetry() {
                retries.incrementAndGet();
            }

            TopicStats snapshot() {
                long count = latencyCount.get();
                return new TopicStats(
                        sent.get(),
                        acknowledged.get(),
                        failed.get(),
                        0,
                        count > 0 ? latencySum.sum() / count : 0.0);
            }
        }
    }
}