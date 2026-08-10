package de.omnistreamforce.cli;

import de.omnistreamforce.web.OmniStreamForceWeb;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
        name = "web",
        description = "Inicia la interfaz web de OmniStreamForce."
)
public class WebCommand implements Callable<Integer> {

    @Option(names = {"-p", "--port"}, description = "Puerto para el servidor web (por defecto: ${DEFAULT-VALUE})", defaultValue = "8080")
    int port;

    @Override
    public Integer call() throws Exception {
        System.out.println("Iniciando OmniStreamForce Web Interface en puerto " + port + "...");
        OmniStreamForceWeb web = new OmniStreamForceWeb(port);
        web.start();
        
        // Wait indefinitely (since Javalin runs in a background thread, 
        // we need to keep the main thread alive)
        Thread.currentThread().join();
        return 0;
    }
}
