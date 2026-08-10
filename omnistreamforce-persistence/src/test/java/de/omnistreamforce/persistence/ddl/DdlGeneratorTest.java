package de.omnistreamforce.persistence.ddl;

import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.domain.ecommerce.EcommerceGenerator;
import de.omnistreamforce.domain.fastfood.FastFoodGenerator;
import de.omnistreamforce.domain.healthcare.HealthcareGenerator;
import de.omnistreamforce.persistence.dialect.PostgresDialect;
import de.omnistreamforce.persistence.dialect.SqlDialect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El DDL se valida contra los EventSchema REALES de los dominios implementados,
 * no contra esquemas de juguete.
 */
class DdlGeneratorTest {

    private final SqlDialect dialect = new PostgresDialect();
    private final DdlGenerator generator = new DdlGenerator(dialect, DdlOptions.defaults());

    private final EventSchema fastfood = new FastFoodGenerator().getSchema();
    private final EventSchema healthcare = new HealthcareGenerator().getSchema();
    private final EventSchema ecommerce = new EcommerceGenerator().getSchema();

    @Test
    void tableIsNamedAfterTheDomain() {
        assertThat(generator.tableFor(fastfood).tableName()).isEqualTo("osf_fastfood_events");
        assertThat(generator.tableFor(healthcare).tableName()).isEqualTo("osf_healthcare_events");
        assertThat(generator.tableFor(ecommerce).tableName()).isEqualTo("osf_ecommerce_events");
    }

    @Test
    void envelopeIsIdenticalAcrossDomains() {
        List<String> expected = List.of("event_id", "event_type", "event_name", "domain", "source",
                "event_ts", "schema_version", "trace_id", "correlation_id", "severity",
                "error_code", "error_message", "message_key", "topic");
        for (EventSchema schema : List.of(fastfood, healthcare, ecommerce)) {
            assertThat(generator.tableFor(schema).envelope().stream().map(ColumnModel::name))
                    .containsExactlyElementsOf(expected);
        }
    }

    @Test
    void fieldNamesBecomeSnakeCaseColumns() {
        Map<String, ColumnModel> bySource = generator.tableFor(fastfood).columnsBySourceField();
        assertThat(bySource.get("brandName").name()).isEqualTo("brand_name");
        assertThat(bySource.get("storeId").name()).isEqualTo("store_id");
        assertThat(bySource.get("prepTimeSeconds").name()).isEqualTo("prep_time_seconds");
        assertThat(bySource.get("quantityOnHand").name()).isEqualTo("quantity_on_hand");
    }

    @Test
    void declaredTypesMapToTheExpectedSqlTypes() {
        Map<String, ColumnModel> bySource = generator.tableFor(fastfood).columnsBySourceField();
        assertThat(bySource.get("storeId").type()).isEqualTo(SqlType.TEXT);        // string
        assertThat(bySource.get("brand").type()).isEqualTo(SqlType.TEXT);          // enum
        assertThat(bySource.get("itemCount").type()).isEqualTo(SqlType.BIGINT);    // int
        assertThat(bySource.get("quantityOnHand").type()).isEqualTo(SqlType.DOUBLE); // double
        assertThat(bySource.get("items").type()).isEqualTo(SqlType.JSON);          // array
    }

    @Test
    void arraysAndNestedObjectsBecomeJson() {
        Map<String, ColumnModel> healthcareColumns = generator.tableFor(healthcare).columnsBySourceField();
        assertThat(healthcareColumns.get("labResults").type()).isEqualTo(SqlType.JSON);  // object
        assertThat(healthcareColumns.get("medications").type()).isEqualTo(SqlType.JSON); // array
        assertThat(generator.tableFor(ecommerce).columnsBySourceField().get("products").type())
                .isEqualTo(SqlType.JSON);
    }

    @Test
    void monetaryFieldsUseExactNumeric() {
        assertThat(generator.tableFor(fastfood).columnsBySourceField().get("totalAmount").type())
                .isEqualTo(SqlType.NUMERIC);
        assertThat(generator.tableFor(ecommerce).columnsBySourceField().get("totalAmount").type())
                .isEqualTo(SqlType.NUMERIC);
    }

    @Test
    void monetaryHeuristicCanBeDisabled() {
        DdlGenerator plain = new DdlGenerator(dialect,
                new DdlOptions("osf_", false, false, false, true));
        assertThat(plain.tableFor(fastfood).columnsBySourceField().get("totalAmount").type())
                .isEqualTo(SqlType.DOUBLE);
    }

    @Test
    void payloadColumnsAreAlwaysNullable() {
        // los 21 EventSpec de fastfood comparten la misma lista de campos: un evento de inventario
        // no lleva orderId, asi que NOT NULL haria fallar la mayoria de los INSERT
        for (EventSchema schema : List.of(fastfood, healthcare, ecommerce)) {
            assertThat(generator.tableFor(schema).payloadColumns())
                    .allMatch(ColumnModel::nullable);
        }
    }

    @Test
    void requiredCanBeEnforcedExplicitly() {
        DdlGenerator strict = new DdlGenerator(dialect,
                new DdlOptions("osf_", true, false, true, true));
        assertThat(strict.tableFor(fastfood).columnsBySourceField().get("brand").nullable()).isFalse();
    }

    @Test
    void overflowColumnsArePresentAndNotNullWhereItMatters() {
        TableModel table = generator.tableFor(fastfood);
        Map<String, ColumnModel> overflow = table.overflow().stream()
                .collect(java.util.stream.Collectors.toMap(ColumnModel::name, c -> c));

        assertThat(overflow).containsOnlyKeys("payload", "payload_extra", "metadata", "ingested_at");
        assertThat(overflow.get("payload").nullable()).isFalse();
        assertThat(overflow.get("payload").type()).isEqualTo(SqlType.JSON);
        assertThat(overflow.get("payload_extra").nullable()).isTrue();
        assertThat(overflow.get("metadata").nullable()).isFalse();
    }

    @Test
    void fullPayloadColumnCanBeDroppedToSaveSpace() {
        DdlGenerator lean = new DdlGenerator(dialect,
                new DdlOptions("osf_", true, false, false, false));
        assertThat(lean.tableFor(fastfood).columnNames()).doesNotContain("payload");
        assertThat(lean.tableFor(fastfood).columnNames()).contains("payload_extra");
    }

    @Test
    void createTableSqlIsValidPostgres() {
        String sql = generator.createTable(generator.tableFor(fastfood));
        assertThat(sql).startsWith("CREATE TABLE IF NOT EXISTS \"osf_fastfood_events\"");
        assertThat(sql).contains("\"event_id\" TEXT NOT NULL");
        assertThat(sql).contains("\"event_ts\" TIMESTAMPTZ NOT NULL");
        assertThat(sql).contains("\"items\" JSONB");
        assertThat(sql).contains("\"total_amount\" NUMERIC(18,4)");
        assertThat(sql).contains("PRIMARY KEY (\"event_id\")");
        assertThat(sql).doesNotContain("DROP");
    }

    @Test
    void indexesCoverTheUsualQueryPaths() {
        List<String> indexes = generator.createIndexes(generator.tableFor(fastfood));
        assertThat(indexes).anyMatch(sql -> sql.contains("\"event_ts\" DESC"));
        assertThat(indexes).anyMatch(sql -> sql.contains("USING gin (\"payload\" jsonb_path_ops)"));
        assertThat(indexes).allMatch(sql -> sql.contains("IF NOT EXISTS"));
    }

    @Test
    void alterStatementsOnlyAddMissingColumnsAndNeverDrop() {
        TableModel table = generator.tableFor(fastfood);
        Set<String> existing = new java.util.LinkedHashSet<>(table.columnNames());
        assertThat(generator.alterStatements(table, existing)).isEmpty();

        existing.remove("store_city");
        existing.remove("items");
        List<String> alters = generator.alterStatements(table, existing);
        assertThat(alters).hasSize(2);
        assertThat(alters).allMatch(sql -> sql.startsWith("ALTER TABLE") && sql.contains("ADD COLUMN IF NOT EXISTS"));
        assertThat(alters).noneMatch(sql -> sql.contains("DROP"));
    }

    @Test
    void columnsAddedLaterAreNeverNotNull() {
        TableModel table = generator.tableFor(fastfood);
        Set<String> existing = new java.util.LinkedHashSet<>(table.columnNames());
        existing.remove("event_ts");   // columna de envelope, declarada NOT NULL

        List<String> alters = generator.alterStatements(table, existing);
        assertThat(alters).hasSize(1);
        assertThat(alters.get(0)).contains("\"event_ts\" TIMESTAMPTZ").doesNotContain("NOT NULL");
    }

    @Test
    void everyDeclaredFieldGetsExactlyOneColumn() {
        TableModel table = generator.tableFor(fastfood);
        List<String> names = table.columnNames();
        assertThat(names).doesNotHaveDuplicates();
        assertThat(table.columnsBySourceField().keySet())
                .containsAll(fastfood.fields().stream().map(f -> f.name()).toList());
    }
}
