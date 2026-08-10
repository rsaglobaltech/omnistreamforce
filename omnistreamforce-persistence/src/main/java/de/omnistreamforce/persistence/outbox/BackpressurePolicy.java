package de.omnistreamforce.persistence.outbox;

/**
 * Que hacer cuando la cola de escritura esta llena, es decir, cuando la base de datos no
 * sigue el ritmo del generador.
 */
public enum BackpressurePolicy {

    /**
     * Bloquear al productor hasta que haya hueco. El EPS baja de forma natural pero no se
     * pierde ningun evento. Es el comportamiento correcto para un generador de datos.
     */
    BLOCK,

    /** Descartar el evento y contarlo. Util cuando importa mas el ritmo que la completitud. */
    DROP,

    /**
     * Lanzar {@link de.omnistreamforce.persistence.PersistenceException}. El motor de generacion
     * la captura y la contabiliza como fallo de publicacion en sus estadisticas.
     */
    FAIL
}
