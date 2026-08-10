package de.omnistreamforce.persistence.ddl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Separa el payload de un evento en la parte que tiene columna propia y la que no.
 * <p>
 * Los generadores anaden al payload claves que el {@code EventSchema} no declara
 * ({@code paidAt}, {@code stationId}, {@code declineCode}, ...). Esas van a
 * {@code payload_extra}, de modo que la deriva de esquema se pueda medir:
 * <pre>
 * SELECT k, count(*) FROM osf_fastfood_events, LATERAL jsonb_object_keys(payload_extra) k
 * GROUP BY 1 ORDER BY 2 DESC;
 * </pre>
 */
public final class PayloadSplitter {

    private PayloadSplitter() {
    }

    /** Claves del payload que no corresponden a ninguna columna declarada. */
    public static Map<String, Object> undeclared(TableModel table, Map<String, Object> payload) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty()) {
            return extra;
        }
        Map<String, ColumnModel> declared = table.columnsBySourceField();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!declared.containsKey(entry.getKey())) {
                extra.put(entry.getKey(), entry.getValue());
            }
        }
        return extra;
    }
}
