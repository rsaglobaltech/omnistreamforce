package de.omnistreamforce.persistence;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.domain.DomainRegistry;
import de.omnistreamforce.engine.CompositePublisher;
import de.omnistreamforce.engine.EventPublisher;
import de.omnistreamforce.kafka.KafkaConnectionManager;
import de.omnistreamforce.kafka.KafkaEventPublisher;
import de.omnistreamforce.persistence.dialect.DialectFactory;
import de.omnistreamforce.persistence.dialect.SqlDialect;
import de.omnistreamforce.persistence.outbox.JdbcOutboxPublisher;
import de.omnistreamforce.persistence.outbox.SchemaCatalog;
import de.omnistreamforce.serializer.EventSerializer;
import de.omnistreamforce.serializer.SerializerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Construye el publisher segun el destino elegido.
 * <p>
 * La firma de {@code MultiDomainEngine(EventSerializer, EventPublisher)} y la de
 * {@code GenerationEngine} no cambian: el motor sigue viendo un {@code EventPublisher} y nada mas.
 *
 * <pre>
 * EventPublisher publisher = PublisherFactory.create(sinkConfig);
 * MultiDomainEngine engine = new MultiDomainEngine(serializer, publisher);
 * </pre>
 */
public final class PublisherFactory {

    private static final Logger log = LoggerFactory.getLogger(PublisherFactory.class);

    private PublisherFactory() {
    }

    public static ManagedPublisher create(SinkConfig config) {
        EventSerializer serializer = SerializerFactory.create(config.serializerFormat());
        List<AutoCloseable> resources = new ArrayList<>();

        KafkaEventPublisher kafkaPublisher = null;
        JdbcOutboxPublisher outboxPublisher = null;

        if (config.type() != SinkType.DB_OUTBOX) {
            KafkaConnectionManager manager = new KafkaConnectionManager(config.kafka());
            resources.add(manager);
            kafkaPublisher = new KafkaEventPublisher(manager.producer(), serializer,
                    config.keyStrategy(), config.keyField(),
                    config.kafkaMaxRetries(), config.kafkaRetryBackoffMs());
        }

        if (config.type() != SinkType.KAFKA) {
            PersistenceConfig persistence = config.persistence();
            SqlDialect dialect = DialectFactory.fromJdbcUrl(persistence.jdbcUrl());
            DataSource dataSource = DataSourceFactory.create(persistence, "osf-sink");
            outboxPublisher = new JdbcOutboxPublisher(dataSource, dialect, persistence, serializer,
                    config.schemaCatalog() != null
                            ? config.schemaCatalog()
                            : SchemaCatalog.fromRegistry(new DomainRegistry()),
                    // misma estrategia de clave que el publisher de Kafka
                    config.keyStrategy(), config.keyField());
            resources.add(outboxPublisher);
            resources.add(() -> DataSourceFactory.close(dataSource));
        }

        EventPublisher delegate = switch (config.type()) {
            case KAFKA -> kafkaPublisher;
            case DB_OUTBOX -> outboxPublisher;
            case DUAL -> new CompositePublisher(List.of(kafkaPublisher, outboxPublisher),
                    config.dualPolicy());
        };

        log.info("Sink configurado: {}", config.type());
        return new ManagedPublisher(delegate, kafkaPublisher, outboxPublisher, resources);
    }

    /**
     * Publisher que ademas se hace cargo de lo que la factory creo (conexion a Kafka, pool JDBC),
     * de modo que {@code MultiDomainEngine.shutdown()} lo libere todo con su unica llamada a
     * {@code close()}.
     */
    public static final class ManagedPublisher implements EventPublisher, AutoCloseable {

        private final EventPublisher delegate;
        private final KafkaEventPublisher kafkaPublisher;
        private final JdbcOutboxPublisher outboxPublisher;
        private final List<AutoCloseable> resources;

        private ManagedPublisher(EventPublisher delegate, KafkaEventPublisher kafkaPublisher,
                                 JdbcOutboxPublisher outboxPublisher, List<AutoCloseable> resources) {
            this.delegate = delegate;
            this.kafkaPublisher = kafkaPublisher;
            this.outboxPublisher = outboxPublisher;
            this.resources = resources;
        }

        @Override
        public void publish(Event event, String topic) {
            delegate.publish(event, topic);
        }

        @Override
        public void publishBatch(List<Event> events, String topic) {
            delegate.publishBatch(events, topic);
        }

        @Override
        public void flush() {
            delegate.flush();
        }

        @Override
        public void close() {
            try {
                delegate.flush();
            } catch (RuntimeException e) {
                log.warn("Error vaciando el publisher al cerrar: {}", e.toString());
            }
            // En orden de registro, NO inverso: los publishers van antes que el pool de
            // conexiones, porque el sink de base de datos necesita el pool vivo para drenar
            // lo que le quede en la cola. Un fallo no impide cerrar el resto.
            for (AutoCloseable resource : resources) {
                try {
                    resource.close();
                } catch (Exception e) {
                    log.warn("Error cerrando un recurso del sink: {}", e.toString());
                }
            }
        }

        public EventPublisher delegate() {
            return delegate;
        }

        public Optional<KafkaEventPublisher> kafkaPublisher() {
            return Optional.ofNullable(kafkaPublisher);
        }

        public Optional<JdbcOutboxPublisher> outboxPublisher() {
            return Optional.ofNullable(outboxPublisher);
        }
    }
}
