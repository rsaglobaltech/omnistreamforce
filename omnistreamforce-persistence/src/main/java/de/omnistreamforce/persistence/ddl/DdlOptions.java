package de.omnistreamforce.persistence.ddl;

/**
 * Opciones de generacion de DDL.
 *
 * @param tablePrefix      prefijo de las tablas generadas ({@code osf_} por defecto)
 * @param moneyAsNumeric   los campos {@code double} cuyo nombre sugiere dinero se emiten como
 *                         NUMERIC exacto: en binario flotante cualquier SUM() arrastra deriva
 *                         de centimos
 * @param enumChecks       anade CHECK (col IN ...) a las columnas enum. Desactivado por defecto:
 *                         un valor nuevo en el dominio haria fallar todos los INSERT
 * @param enforceRequired  traduce {@code required=true} a NOT NULL en columnas de payload.
 *                         Desactivado por defecto porque los dominios comparten la misma lista de
 *                         campos entre todos sus tipos de evento (un evento de inventario no lleva
 *                         orderId), asi que casi ningun campo esta presente en todas las filas
 * @param storeFullPayload guarda ademas el payload completo en la columna {@code payload},
 *                         para poder reconstruir el Event exacto
 */
public record DdlOptions(
        String tablePrefix,
        boolean moneyAsNumeric,
        boolean enumChecks,
        boolean enforceRequired,
        boolean storeFullPayload
) {
    public static final String DEFAULT_TABLE_PREFIX = "osf_";

    public DdlOptions {
        tablePrefix = tablePrefix == null ? "" : tablePrefix;
    }

    public static DdlOptions defaults() {
        return new DdlOptions(DEFAULT_TABLE_PREFIX, true, false, false, true);
    }
}
