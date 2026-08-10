package de.omnistreamforce.ai.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Transporte sobre el cliente HTTP del JDK: sin dependencias externas.
 */
public class JdkHttpTransport implements HttpTransport {

    private final HttpClient client;
    private final Duration timeout;

    public JdkHttpTransport(long timeoutMs) {
        this.timeout = Duration.ofMillis(timeoutMs);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public Response post(String url, String body, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(request::header);

        try {
            HttpResponse<String> response = client.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new HttpTransportException("Fallo de red llamando a " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpTransportException("Llamada interrumpida a " + url, e);
        }
    }
}
