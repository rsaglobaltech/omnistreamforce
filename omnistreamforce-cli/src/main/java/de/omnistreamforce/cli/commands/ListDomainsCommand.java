package de.omnistreamforce.cli.commands;

import de.omnistreamforce.cli.console.ConsoleRenderer;
import de.omnistreamforce.domain.DomainGenerator;
import de.omnistreamforce.domain.DomainRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Lista los dominios descubiertos en el classpath via ServiceLoader.
 */
@Command(
        name = "list-domains",
        description = "Lista los dominios disponibles y sus tipos de evento."
)
public class ListDomainsCommand implements Callable<Integer> {

    @Option(names = {"-d", "--detail"}, description = "Muestra los campos declarados de cada dominio")
    boolean detail;

    @Override
    public Integer call() {
        ConsoleRenderer console = new ConsoleRenderer();
        DomainRegistry registry = new DomainRegistry();

        if (registry.size() == 0) {
            console.warn("No hay dominios registrados en el classpath.");
            return 1;
        }

        List<List<String>> rows = new ArrayList<>();
        for (DomainGenerator generator : registry.listGenerators()) {
            rows.add(List.of(
                    generator.getDomainName(),
                    String.valueOf(generator.getSupportedEventTypes().size()),
                    String.valueOf(generator.getErrorTypes().size()),
                    generator.getSchema().name()));
        }
        console.title("Dominios disponibles (" + registry.size() + ")");
        console.table(List.of("Dominio", "Eventos normales", "Eventos de error", "Esquema"), rows);

        if (detail) {
            for (DomainGenerator generator : registry.listGenerators()) {
                console.title(generator.getDomainName());
                console.info(generator.getSchema().description());
                console.info("Normales: " + String.join(", ", generator.getSupportedEventTypes()));
                console.info("Errores : " + String.join(", ", generator.getErrorTypes()));
                List<List<String>> fields = new ArrayList<>();
                generator.getSchema().fields().forEach(field -> fields.add(List.of(
                        field.name(),
                        field.type(),
                        field.required() ? "si" : "no",
                        field.description() == null ? "" : field.description())));
                console.table(List.of("Campo", "Tipo", "Requerido", "Descripcion"), fields);
            }
        }
        return 0;
    }
}
