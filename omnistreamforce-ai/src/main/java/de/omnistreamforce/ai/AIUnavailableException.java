package de.omnistreamforce.ai;

/**
 * El modelo no esta disponible: desactivado, sin credenciales, saturado o inalcanzable.
 * Quien la recibe debe seguir sin IA, no abortar.
 */
public class AIUnavailableException extends RuntimeException {

    public AIUnavailableException(String message) {
        super(message);
    }

    public AIUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
