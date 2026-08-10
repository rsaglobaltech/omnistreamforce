package de.omnistreamforce.persistence;

/**
 * La base de datos no esta disponible: el circuito esta abierto tras varios lotes fallidos
 * consecutivos. Se lanza desde {@code publish()} para que el fallo, que ocurre de forma asincrona
 * en los hilos de escritura, sea visible en las estadisticas del motor.
 */
public class PersistenceUnavailableException extends PersistenceException {

    public PersistenceUnavailableException(String message) {
        super(message);
    }

    public PersistenceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
