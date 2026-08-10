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
                WebCommand.class
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

    static void printBanner() {
        String banner = """
                  ___                  ___                    ___           ___
                 /\\  \\                /\\  \\                  /\\  \\         /\\  \\
                |  \\  \\              |  \\  \\                |  \\  \\       |  \\  \\
                |(___\\ \\              \\ \\  \\                 |(__ \\ \\      \\ \\  \\
                  /\\_\\ \\              /\\ \\  \\                  /\\_\\ \\      |  \\  \\
                 / /__/ /            |  \\ \\  \\                / /__/ /     / /  /
                |__|__\\/              \\ |__|__|               |__|__\\/     |__|__\\/
                  ___           ___    ___           ___           ___           ___
                 /\\__\\         /\\__\\  /\\__\\         /\\__\\         /\\__\\         /\\__\\
                / /  /        / /  / / /  /        / /  /        / /  /        / /  /
                \\ \\  \\        \\ \\  \\ \\ \\  \\        \\ \\  \\        \\ \\  \\        \\ \\  \\
                 \\ \\__\\        \\ \\__\\ \\ \\__\\        \\ \\__\\        \\ \\__\\        \\ \\__\\
                  \\/__/         \\/__/  \\/__/         \\/__/         \\/__/         \\/__/
                """;
        System.out.println(banner);
    }
}