package de.omnistreamforce.kafka;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuracion de conexion a un cluster de Kafka.
 * Soporta PLAINTEXT, SSL, SASL_PLAINTEXT y SASL_SSL, asi como los perfiles
 * de AWS MSK (SCRAM/IAM) y Confluent Cloud (SASL_PLAIN con API key/secret).
 */
public record KafkaConnectionConfig(
        ClusterType clusterType,
        String bootstrapServers,
        String securityProtocol,
        String saslMechanism,
        String saslJaasConfig,
        String sslTruststoreLocation,
        String sslTruststorePassword,
        String sslKeystoreLocation,
        String sslKeystorePassword,
        String clientId,
        String acks,
        int retries,
        int batchSize,
        int lingerMs,
        long bufferMemory,
        String compressionType,
        int maxInFlightRequestsPerConnection,
        Map<String, String> extraProps
) {
    public KafkaConnectionConfig {
        if (extraProps != null) {
            extraProps = Map.copyOf(extraProps);
        } else {
            extraProps = Map.of();
        }
    }

    public Properties toProducerProps() {
        Properties props = baseProps();
        if (acks != null) props.put("acks", acks);
        if (retries >= 0) props.put("retries", retries);
        if (batchSize > 0) props.put("batch.size", batchSize);
        if (lingerMs >= 0) props.put("linger.ms", lingerMs);
        if (bufferMemory > 0) props.put("buffer.memory", bufferMemory);
        if (compressionType != null) props.put("compression.type", compressionType);
        if (maxInFlightRequestsPerConnection > 0) {
            props.put("max.in.flight.requests.per.connection", maxInFlightRequestsPerConnection);
        }
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        return props;
    }

    public Properties toAdminProps() {
        Properties props = baseProps();
        return props;
    }

    private Properties baseProps() {
        Properties props = new Properties();
        if (bootstrapServers != null) props.put("bootstrap.servers", bootstrapServers);
        if (clientId != null) props.put("client.id", clientId);
        if (securityProtocol != null) props.put("security.protocol", securityProtocol);
        if (saslMechanism != null) props.put("sasl.mechanism", saslMechanism);
        if (saslJaasConfig != null) props.put("sasl.jaas.config", saslJaasConfig);
        if (sslTruststoreLocation != null) props.put("ssl.truststore.location", sslTruststoreLocation);
        if (sslTruststorePassword != null) props.put("ssl.truststore.password", sslTruststorePassword);
        if (sslKeystoreLocation != null) props.put("ssl.keystore.location", sslKeystoreLocation);
        if (sslKeystorePassword != null) props.put("ssl.keystore.password", sslKeystorePassword);
        for (Map.Entry<String, String> e : extraProps.entrySet()) {
            props.put(e.getKey(), e.getValue());
        }
        return props;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ClusterType clusterType = ClusterType.LOCAL;
        private String bootstrapServers = "localhost:9092";
        private String securityProtocol = "PLAINTEXT";
        private String saslMechanism;
        private String saslJaasConfig;
        private String sslTruststoreLocation;
        private String sslTruststorePassword;
        private String sslKeystoreLocation;
        private String sslKeystorePassword;
        private String clientId = "omnistreamforce";
        private String acks = "all";
        private int retries = 3;
        private int batchSize;
        private int lingerMs;
        private long bufferMemory;
        private String compressionType;
        private int maxInFlightRequestsPerConnection;
        private final Map<String, String> extraProps = new LinkedHashMap<>();

        public Builder clusterType(ClusterType v) { this.clusterType = v; return this; }
        public Builder bootstrapServers(String v) { this.bootstrapServers = v; return this; }
        public Builder securityProtocol(String v) { this.securityProtocol = v; return this; }
        public Builder saslMechanism(String v) { this.saslMechanism = v; return this; }
        public Builder saslJaasConfig(String v) { this.saslJaasConfig = v; return this; }
        public Builder sslTruststoreLocation(String v) { this.sslTruststoreLocation = v; return this; }
        public Builder sslTruststorePassword(String v) { this.sslTruststorePassword = v; return this; }
        public Builder sslKeystoreLocation(String v) { this.sslKeystoreLocation = v; return this; }
        public Builder sslKeystorePassword(String v) { this.sslKeystorePassword = v; return this; }
        public Builder clientId(String v) { this.clientId = v; return this; }
        public Builder acks(String v) { this.acks = v; return this; }
        public Builder retries(int v) { this.retries = v; return this; }
        public Builder batchSize(int v) { this.batchSize = v; return this; }
        public Builder lingerMs(int v) { this.lingerMs = v; return this; }
        public Builder bufferMemory(long v) { this.bufferMemory = v; return this; }
        public Builder compressionType(String v) { this.compressionType = v; return this; }
        public Builder maxInFlight(int v) { this.maxInFlightRequestsPerConnection = v; return this; }
        public Builder extraProp(String k, String v) { this.extraProps.put(k, v); return this; }

        public KafkaConnectionConfig build() {
            return new KafkaConnectionConfig(clusterType, bootstrapServers, securityProtocol,
                    saslMechanism, saslJaasConfig, sslTruststoreLocation, sslTruststorePassword,
                    sslKeystoreLocation, sslKeystorePassword, clientId, acks, retries, batchSize,
                    lingerMs, bufferMemory, compressionType, maxInFlightRequestsPerConnection,
                    extraProps);
        }
    }
}