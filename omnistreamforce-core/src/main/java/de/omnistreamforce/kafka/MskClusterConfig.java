package de.omnistreamforce.kafka;

import java.util.Properties;

/**
 * Modelo de configuracion especifica para AWS MSK.
 * <p>
 * Detecta el tipo de autenticacion (SASL/SCRAM o IAM) y genera las propiedades
 * del producer apropiadas. {@code toKafkaProps()} no requiere conectarse a AWS,
 * solo construye la configuracion del cliente Kafka.
 */
public record MskClusterConfig(
        String bootstrapBrokers,
        AuthType authType,
        String awsRegion,
        String mskArn,
        String scramUsername,
        String scramPassword
) {
    public enum AuthType {
        SCRAM,
        IAM
    }

    /**
     * Genera las propiedades del producer para AWS MSK segun el tipo de auth.
     */
    public Properties toKafkaProps() {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapBrokers);
        props.put("security.protocol", "SASL_SSL");

        switch (authType) {
            case IAM -> {
                props.put("sasl.mechanism", "AWS_MSK_IAM");
                props.put("sasl.jaas.config",
                        "software.amazon.msk.auth.iam.IAMLoginModule required "
                                + "awsProfile=\"" + defaultIfNull(awsProfile(), "default") + "\";");
                props.put("sasl.client.callback.handler.class",
                        "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
                if (awsRegion != null) {
                    props.put("aws.region", awsRegion);
                }
            }
            case SCRAM -> {
                props.put("sasl.mechanism", "SCRAM-SHA-512");
                String jaas = "org.apache.kafka.common.security.scram.ScramLoginModule required "
                        + "username=\"" + scramUsername + "\" "
                        + "password=\"" + scramPassword + "\";";
                props.put("sasl.jaas.config", jaas);
            }
        }
        return props;
    }

    private String awsProfile() {
        return null;
    }

    private static String defaultIfNull(String value, String fallback) {
        return value == null ? fallback : value;
    }

    /**
     * Propiedades para usar endpoints publicos de MSK (con SASL sobre TLS).
     */
    public Properties toPublicProps() {
        return toKafkaProps();
    }
}