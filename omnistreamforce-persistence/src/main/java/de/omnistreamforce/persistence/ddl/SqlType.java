package de.omnistreamforce.persistence.ddl;

/**
 * Tipos de columna que el generador de DDL sabe emitir, independientes del motor.
 * Cada {@link de.omnistreamforce.persistence.dialect.SqlDialect} los traduce a su sintaxis.
 */
public enum SqlType {

    /** Texto sin longitud fija. */
    TEXT,
    /** Entero de 64 bits: los rangos de los generadores son arbitrarios, no se usa INTEGER. */
    BIGINT,
    /** Coma flotante de doble precision. */
    DOUBLE,
    /** Decimal exacto, para importes monetarios. */
    NUMERIC,
    /** Documento JSON (jsonb en Postgres): arrays, objetos anidados y overflow de payload. */
    JSON,
    /** Instante con zona horaria, siempre almacenado en UTC. */
    TIMESTAMPTZ,
    BOOLEAN
}
