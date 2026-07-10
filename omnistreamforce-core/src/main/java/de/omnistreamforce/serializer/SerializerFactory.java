package de.omnistreamforce.serializer;

/**
 * Factory para crear el {@link EventSerializer} segun el formato solicitado.
 */
public class SerializerFactory {

    public static EventSerializer create(String format) {
        if (format == null) {
            return new JsonEventSerializer();
        }
        return switch (format.toUpperCase()) {
            case "JSON" -> new JsonEventSerializer();
            case "AVRO" -> new AvroEventSerializer();
            case "PROTOBUF", "PROTO" -> new ProtobufEventSerializer();
            default -> throw new IllegalArgumentException("Formato de serializacion no soportado: " + format);
        };
    }
}