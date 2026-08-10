package de.omnistreamforce.persistence;

/**
 * Fallo de persistencia. Es no chequeada a proposito: {@code EventPublisher.publish} no declara
 * excepciones y el motor de generacion ya captura {@code RuntimeException} para contabilizar el
 * fallo en sus estadisticas.
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
