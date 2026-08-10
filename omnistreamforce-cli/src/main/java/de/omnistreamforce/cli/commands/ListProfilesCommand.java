package de.omnistreamforce.cli.commands;

import de.omnistreamforce.cli.console.ConsoleRenderer;
import de.omnistreamforce.config.ConfigManager;
import de.omnistreamforce.config.OmniStreamForceConfig;
import de.omnistreamforce.config.ProfileLoader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Lista los perfiles disponibles y, opcionalmente, muestra el contenido de uno.
 */
@Command(
        name = "list-profiles",
        description = "Lista los perfiles de configuracion disponibles."
)
public class ListProfilesCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "Perfil del que mostrar el detalle")
    String name;

    @Option(names = {"--yaml"}, description = "Vuelca el perfil en YAML, listo para editar")
    boolean asYaml;

    @Override
    public Integer call() {
        ConsoleRenderer console = new ConsoleRenderer();
        ProfileLoader loader = new ProfileLoader();

        if (name == null) {
            List<String> profiles = loader.listProfiles();
            console.title("Perfiles disponibles (" + profiles.size() + ")");
            List<List<String>> rows = new ArrayList<>();
            for (String profile : profiles) {
                try {
                    OmniStreamForceConfig config = loader.loadProfile(profile);
                    rows.add(List.of(profile,
                            String.valueOf(config.domains().size()),
                            String.valueOf(config.totalEventsPerSecond()),
                            config.generation().publishingMode().name(),
                            config.sink().type()));
                } catch (RuntimeException e) {
                    rows.add(List.of(profile, "?", "?", "?", "ilegible"));
                }
            }
            console.table(List.of("Perfil", "Dominios", "EPS total", "Modo", "Destino"), rows);
            console.info("Detalle: omnistreamforce list-profiles <perfil> [--yaml]");
            return 0;
        }

        OmniStreamForceConfig config;
        try {
            config = loader.loadProfile(name);
        } catch (RuntimeException e) {
            console.error(e.getMessage());
            return 1;
        }

        if (asYaml) {
            java.io.StringWriter writer = new java.io.StringWriter();
            org.yaml.snakeyaml.DumperOptions options = new org.yaml.snakeyaml.DumperOptions();
            options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
            options.setIndent(2);
            new org.yaml.snakeyaml.Yaml(options).dump(config.toMap(), writer);
            console.println(writer.toString());
            return 0;
        }

        console.title("Perfil: " + name);
        List<List<String>> rows = new ArrayList<>();
        for (OmniStreamForceConfig.DomainMapping domain : config.domains()) {
            rows.add(List.of(domain.domain(), domain.topic(), domain.resolvedErrorTopic(),
                    String.valueOf(domain.eventsPerSecond()),
                    String.format(Locale.ROOT, "%.1f%%", domain.errorRate()),
                    domain.keyStrategy().name()));
        }
        console.table(List.of("Dominio", "Topic", "Topic de errores", "EPS", "Errores", "Clave"), rows);
        console.info("Cluster: " + config.kafka().clusterType() + " en " + config.kafka().bootstrapServers());
        console.info("Destino: " + config.sink().type()
                + " | formato: " + config.serialization().format()
                + " | modo: " + config.generation().publishingMode());

        List<String> errors = new ConfigManager().validate(config);
        if (errors.isEmpty()) {
            console.success("El perfil es valido");
        } else {
            errors.forEach(console::error);
            return 1;
        }
        return 0;
    }
}
