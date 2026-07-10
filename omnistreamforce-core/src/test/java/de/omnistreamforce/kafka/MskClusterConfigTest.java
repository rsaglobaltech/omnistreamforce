package de.omnistreamforce.kafka;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MskClusterConfigTest {

    @Test
    void iamAuthProducesCorrectProps() {
        MskClusterConfig config = new MskClusterConfig(
                "b-1.cluster.abc.c2.kafka.us-east-1.amazonaws.com:9094",
                MskClusterConfig.AuthType.IAM, "us-east-1", "arn:aws:kafka:us-east-1:123:cluster/demo", null, null);

        Properties props = config.toKafkaProps();

        assertThat(props.getProperty("bootstrap.servers")).contains("amazonaws.com:9094");
        assertThat(props.getProperty("security.protocol")).isEqualTo("SASL_SSL");
        assertThat(props.getProperty("sasl.mechanism")).isEqualTo("AWS_MSK_IAM");
        assertThat(props.getProperty("sasl.jaas.config")).contains("IAMLoginModule");
        assertThat(props.getProperty("sasl.client.callback.handler.class"))
                .isEqualTo("software.amazon.msk.auth.iam.IAMClientCallbackHandler");
        assertThat(props.getProperty("aws.region")).isEqualTo("us-east-1");
    }

    @Test
    void scramAuthProducesCorrectProps() {
        MskClusterConfig config = new MskClusterConfig(
                "b-1.cluster.abc.c2.kafka.us-east-1.amazonaws.com:9092",
                MskClusterConfig.AuthType.SCRAM, "us-east-1", null, "user1", "pass1");

        Properties props = config.toKafkaProps();

        assertThat(props.getProperty("security.protocol")).isEqualTo("SASL_SSL");
        assertThat(props.getProperty("sasl.mechanism")).isEqualTo("SCRAM-SHA-512");
        assertThat(props.getProperty("sasl.jaas.config"))
                .contains("ScramLoginModule")
                .contains("username=\"user1\"")
                .contains("password=\"pass1\"");
    }

    @Test
    void publicPropsEquivalentForEndpoints() {
        MskClusterConfig config = new MskClusterConfig(
                "b-2.pub.cluster.abc.c2.kafka.us-east-1.amazonaws.com:9094",
                MskClusterConfig.AuthType.IAM, "us-east-1", null, null, null);
        assertThat(config.toPublicProps().getProperty("bootstrap.servers")).contains(".pub.");
    }
}