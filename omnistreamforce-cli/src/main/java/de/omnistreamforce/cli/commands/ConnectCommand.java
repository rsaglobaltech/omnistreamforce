package de.omnistreamforce.cli.commands;

import de.omnistreamforce.cli.cluster.KafkaClusterGateway;
import de.omnistreamforce.cli.console.ConsoleRenderer;
import de.omnistreamforce.kafka.ClusterInfo;
import de.omnistreamforce.kafka.ClusterType;
import de.omnistreamforce.kafka.KafkaConnectionConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Valida la conexion a un cluster y lista sus topics.
 */
@Command(
        name = "connect",
        description = "Conecta a un cluster de Kafka, valida el acceso y lista sus topics."
)
public class ConnectCommand implements Callable<Integer> {

    @Option(names = {"-b", "--bootstrap-servers"}, description = "Bootstrap servers (por defecto: ${DEFAULT-VALUE})",
            defaultValue = "localhost:9092")
    String bootstrapServers;

    @Option(names = {"--security-protocol"}, description = "PLAINTEXT, SSL, SASL_PLAINTEXT o SASL_SSL",
            defaultValue = "PLAINTEXT")
    String securityProtocol;

    @Option(names = {"--topics"}, description = "Lista tambien los topics del cluster", defaultValue = "true")
    boolean listTopics;

    @Override
    public Integer call() {
        ConsoleRenderer console = new ConsoleRenderer();
        KafkaConnectionConfig config = KafkaConnectionConfig.builder()
                .clusterType(ClusterType.LOCAL)
                .bootstrapServers(bootstrapServers)
                .securityProtocol(securityProtocol)
                .clientId("omnistreamforce-connect")
                .build();

        console.title("Conexion a " + bootstrapServers);
        try (KafkaClusterGateway gateway = new KafkaClusterGateway(config)) {
            ClusterInfo info = gateway.connect();
            console.success("Conexion establecida");
            console.info("Cluster ID : " + info.clusterId());
            console.info("Tipo       : " + info.clusterType());
            console.info("Brokers    : " + String.join(", ", info.brokers()));
            console.info("Version    : " + info.kafkaVersion());
            console.info("Controlador: " + info.controllerId());

            if (listTopics) {
                Map<String, Integer> topics = gateway.listTopicsWithPartitions();
                List<List<String>> rows = new ArrayList<>();
                topics.forEach((name, partitions) -> rows.add(List.of(name, String.valueOf(partitions))));
                console.title("Topics (" + topics.size() + ")");
                if (rows.isEmpty()) {
                    console.info("(el cluster no tiene topics)");
                } else {
                    console.table(List.of("Topic", "Particiones"), rows);
                }
            }
            return 0;
        } catch (RuntimeException e) {
            console.error("No se pudo conectar: " + e.getMessage());
            return 1;
        }
    }
}
