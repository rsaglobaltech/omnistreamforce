package de.omnistreamforce.serializer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerializerFactoryTest {

    @Test
    void createsJsonSerializer() {
        assertThat(SerializerFactory.create("JSON")).isInstanceOf(JsonEventSerializer.class);
    }

    @Test
    void createsAvroSerializer() {
        assertThat(SerializerFactory.create("AVRO")).isInstanceOf(AvroEventSerializer.class);
    }

    @Test
    void createsProtobufSerializer() {
        assertThat(SerializerFactory.create("PROTOBUF")).isInstanceOf(ProtobufEventSerializer.class);
    }

    @Test
    void defaultsToJsonWhenNull() {
        assertThat(SerializerFactory.create(null)).isInstanceOf(JsonEventSerializer.class);
    }

    @Test
    void throwsForUnsupportedFormat() {
        assertThatThrownBy(() -> SerializerFactory.create("YAML"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}