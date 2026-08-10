package de.omnistreamforce.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Year;

/**
 * Punto de entrada principal de OmniStreamForce CLI.
 */
@Command(
        name = "omnistreamforce",
        mixinStandardHelpOptions = true,
        version = "OmniStreamForce 1.0.0-SNAPSHOT",
        description = "Generador de datos configurable y adaptable para Apache Kafka con soporte multi-dominio, multi-topic y multi-plataforma potenciado por IA.",
        subcommands = {
                CommandLine.HelpCommand.class,
                WebCommand.class,
                de.omnistreamforce.cli.commands.InteractiveCommand.class,
                de.omnistreamforce.cli.commands.BatchCommand.class,
                de.omnistreamforce.cli.commands.ConnectCommand.class,
                de.omnistreamforce.cli.commands.ListDomainsCommand.class,
                de.omnistreamforce.cli.commands.ListProfilesCommand.class
        },
        headerHeading = "%n",
        synopsisHeading = "%nUso: ",
        parameterListHeading = "%nParametros:%n",
        optionListHeading = "%nOpciones:%n"
)
public class OmniStreamForceApp implements Runnable {

    @Option(names = {"-v", "--verbose"}, description = "Modo verbose (mas logs)")
    boolean verbose;

    public static void main(String[] args) {
        printBanner();
        int exitCode = new CommandLine(new OmniStreamForceApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println();
        System.out.println("  OmniStreamForce - Data Stream Generator for Apache Kafka");
        System.out.println("  Plataforma detectada: " + detectPlatform());
        System.out.println("  Java: " + System.getProperty("java.version"));
        System.out.println("  (C) " + Year.now() + " OmniStreamForce");
        System.out.println();
        System.out.println("  Ejecuta 'omnistreamforce help' para ver los comandos disponibles.");
        System.out.println();
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "unknown").toLowerCase();
        if (os.contains("win")) {
            return "Windows";
        } else if (os.contains("mac")) {
            return "macOS";
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return "Linux";
        }
        return os;
    }

    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String RESET = "\u001B[0m";

    static void printBanner() {
        boolean unicode = supportsUnicode();
        boolean colour = supportsColour();

        String art = """
                  ___  __  __ _  _ ___ ___ _____ ___ ___   _   __  __
                 / _ \\|  \\/  | \\| |_ _/ __|_   _| _ \\ __| /_\\ |  \\/  |
                | (_) | |\\/| | .` || |\\__ \\ | | |   / _| / _ \\| |\\/| |
                 \\___/|_|  |_|_|\\_|___|___/ |_| |_|_\\___/_/ \\_\\_|  |_|
                            ___ ___  ___  ___ ___
                           | __/ _ \\| _ \\/ __| __|
                           | _| (_) |   / (__| _|
                           |_| \\___/|_|_\\___|___|
                """;

        String line = unicode ? "─".repeat(58) : "-".repeat(58);
        StringBuilder sb = new StringBuilder();
        sb.append(System.lineSeparator());
        sb.append(colour ? CYAN + BOLD + art + RESET : art);
        sb.append("  ").append(line).append(System.lineSeparator());
        sb.append("  Generador de datos para Apache Kafka")
                .append(System.lineSeparator());
        String tagline = unicode
                ? "  multi-dominio · multi-topic · outbox & CDC · " + detectPlatform()
                : "  multi-dominio | multi-topic | outbox & CDC | " + detectPlatform();
        sb.append(colour ? DIM + tagline + RESET : tagline).append(System.lineSeparator());
        sb.append("  ").append(line).append(System.lineSeparator());

        System.out.println(sb);
    }

    /** Sin UTF-8 en la salida los caracteres de dibujo se convierten en basura. */
    private static boolean supportsUnicode() {
        String encoding = System.getProperty("stdout.encoding",
                System.getProperty("file.encoding", ""));
        return encoding.toUpperCase(java.util.Locale.ROOT).contains("UTF");
    }

    private static boolean supportsColour() {
        if (System.console() == null) {
            return false;
        }
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return !os.contains("win")
                || System.getenv("WT_SESSION") != null
                || System.getenv("ConEmuANSI") != null;
    }
}