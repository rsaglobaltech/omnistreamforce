package de.omnistreamforce.ai.http;

import java.util.Map;

/**
 * Lo minimo que los servicios de IA necesitan de HTTP.
 * <p>
 * Existe para poder probar los servicios con respuestas preparadas, sin red ni servidor.
 */
public interface HttpTransport {

    /**
     * @return cuerpo y codigo de la respuesta
     * @throws HttpTransportException si la peticion no llega a completarse
     */
    Response post(String url, String body, Map<String, String> headers);

    record Response(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }

        /** Merece la pena reintentar: limite de peticiones o error temporal del servidor. */
        public boolean isRetryable() {
            return status == 429 || status >= 500;
        }
    }

    class HttpTransportException extends RuntimeException {
        public HttpTransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
