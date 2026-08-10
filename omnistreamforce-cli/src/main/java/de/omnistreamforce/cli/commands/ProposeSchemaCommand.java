package de.omnistreamforce.cli.commands;

import de.omnistreamforce.ai.AIConfig;
import de.omnistreamforce.ai.AIProvider;
import de.omnistreamforce.ai.AIService;
import de.omnistreamforce.ai.AIServiceFactory;
import de.omnistreamforce.ai.SchemaProposer;
import de.omnistreamforce.ai.domain.SchemaBackedGenerator;
import de.omnistreamforce.cli.console.ConsoleRenderer;
import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.EventType;
import de.omnistreamforce.domain.DomainRegistry;
import de.omnistreamforce.serializer.SerializerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Pide a un modelo el esquema de un dominio que no existe todavia y muestra como quedarian sus
 * eventos.
 */
@Command(
        name = "propose-schema",
        description = "Propone con IA el esquema de eventos de un dominio nuevo."
)
public class ProposeSchemaCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Nombre del dominio (por ejemplo: logistics)")
    String domain;

    @Option(names = {"-d", "--description"}, description = "Contexto del dominio en una frase")
    String description;

    @Option(names = {"--provider"}, description = "openai u ollama (por defecto: ${DEFAULT-VALUE})",
            defaultValue = "ollama")
    String provider;

    @Option(names = {"--model"}, description = "Modelo a usar")
    String model;

    @Option(names = {"--base-url"}, description = "URL del proveedor (proxy o instancia propia)")
    String baseUrl;

    @Option(names = {"--api-key"}, description = "Clave de API; por defecto se toma de OSF_AI_API_KEY")
    String apiKey;

    @Option(names = {"--sample"}, description = "Cuantos eventos de ejemplo mostrar",
            defaultValue = "2")
    int sample;

    @Override
    public Integer call() {
        ConsoleRenderer console = new ConsoleRenderer();

        AIProvider aiProvider;
        try {
            aiProvider = AIProvider.valueOf(provider.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            console.error("Proveedor desconocido: " + provider + " (usa openai u ollama)");
            return 1;
        }

        AIConfig config = AIConfig.builder(aiProvider)
                .apiKey(apiKey != null ? apiKey : System.getenv("OSF_AI_API_KEY"))
                .model(model)
                .baseUrl(baseUrl)
                .build();

        if (new DomainRegistry().isAvailable(domain)) {
            console.warn("Ya existe un dominio '" + domain + "' en el classpath; "
                    + "se propondra uno nuevo sin sustituirlo.");
        }

        try (AIService service = AIServiceFactory.create(config)) {
            console.title("Proponiendo esquema para '" + domain + "' con " + service.providerName());
            SchemaProposer proposer = new SchemaProposer(service);

            EventSchema schema;
            try {
                schema = proposer.propose(domain, description);
            } catch (RuntimeException e) {
                console.error(e.getMessage() == null ? e.toString() : e.getMessage());
                console.info("La IA es opcional: sin ella siguen disponibles los dominios "
                        + "registrados (list-domains).");
                return 1;
            }

            showSchema(console, schema);
            showSamples(console, schema);
            console.success("Esquema listo. Para publicarlo, registralo en el motor con "
                    + "SchemaBackedGenerator.");
            return 0;
        } catch (Exception e) {
            console.error("Error inesperado: " + e.getMessage());
            return 1;
        }
    }

    private void showSchema(ConsoleRenderer console, EventSchema schema) {
        console.info(schema.description());
        List<List<String>> rows = new ArrayList<>();
        schema.fields().forEach(field -> rows.add(List.of(
                field.name(),
                field.type(),
                field.required() ? "si" : "no",
                field.enumValues() == null || field.enumValues().isEmpty()
                        ? "" : String.join(", ", field.enumValues()))));
        console.table(List.of("Campo", "Tipo", "Requerido", "Valores"), rows);

        console.info("Eventos normales: " + schema.eventTypes().stream()
                .filter(spec -> spec.eventType() != EventType.ERROR)
                .map(spec -> spec.typeName()).toList());
        console.info("Eventos de error: " + schema.eventTypes().stream()
                .filter(spec -> spec.eventType() == EventType.ERROR)
                .map(spec -> spec.typeName()).toList());
    }

    private void showSamples(ConsoleRenderer console, EventSchema schema) {
        SchemaBackedGenerator generator = new SchemaBackedGenerator(schema);
        var serializer = SerializerFactory.create("JSON");

        console.title("Eventos de ejemplo");
        for (int i = 0; i < Math.max(1, sample); i++) {
            Event event = i % 2 == 0 || generator.getErrorTypes().isEmpty()
                    ? generator.generateEvent(generator.getSupportedEventTypes()
                    .get(i % generator.getSupportedEventTypes().size()))
                    : generator.generateErrorEvent();
            console.println(new String(serializer.serialize(event), StandardCharsets.UTF_8));
        }
    }
}
