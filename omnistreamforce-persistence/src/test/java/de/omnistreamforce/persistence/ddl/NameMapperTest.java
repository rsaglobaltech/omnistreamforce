package de.omnistreamforce.persistence.ddl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameMapperTest {

    @Test
    void convertsCamelCaseToSnakeCase() {
        assertThat(NameMapper.toSnakeCase("orderId")).isEqualTo("order_id");
        assertThat(NameMapper.toSnakeCase("prepTimeSeconds")).isEqualTo("prep_time_seconds");
        assertThat(NameMapper.toSnakeCase("OrderPlaced")).isEqualTo("order_placed");
        assertThat(NameMapper.toSnakeCase("oxygenSaturation")).isEqualTo("oxygen_saturation");
        assertThat(NameMapper.toSnakeCase("already_snake")).isEqualTo("already_snake");
    }

    @Test
    void handlesAcronymsAndSeparators() {
        assertThat(NameMapper.toSnakeCase("gpsLocation")).isEqualTo("gps_location");
        assertThat(NameMapper.toSnakeCase("customerID")).isEqualTo("customer_id");
        assertThat(NameMapper.toSnakeCase("kebab-case name")).isEqualTo("kebab_case_name");
        assertThat(NameMapper.toSnakeCase("weird!!name")).isEqualTo("weird_name");
    }

    @Test
    void identifierNeverStartsWithADigit() {
        assertThat(NameMapper.toSnakeCase("2ndAttempt")).startsWith("f_2");
    }

    @Test
    void fieldsCollidingWithTheEnvelopeArePrefixed() {
        NameMapper mapper = new NameMapper(63);
        NameMapper.RESERVED.forEach(mapper::reserve);

        assertThat(mapper.columnFor("domain")).isEqualTo("f_domain");
        assertThat(mapper.columnFor("payload")).isEqualTo("f_payload");
        assertThat(mapper.columnFor("topic")).isEqualTo("f_topic");
        assertThat(mapper.columnFor("severity")).isEqualTo("f_severity");
    }

    @Test
    void longNamesAreTruncatedToTheDialectLimit() {
        NameMapper mapper = new NameMapper(20);
        String column = mapper.columnFor("aVeryLongFieldNameThatExceedsTheLimitByFar");
        assertThat(column).hasSize(20);
    }

    @Test
    void collisionsAfterTruncationGetASuffix() {
        NameMapper mapper = new NameMapper(20);
        String first = mapper.columnFor("aVeryLongFieldNameNumberOne");
        String second = mapper.columnFor("aVeryLongFieldNameNumberTwo");

        assertThat(first).isNotEqualTo(second);
        assertThat(second).endsWith("_2");
        assertThat(second.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void tableNameUsesThePrefixAndDomain() {
        assertThat(NameMapper.tableName("fastfood", DdlOptions.defaults(), 63))
                .isEqualTo("osf_fastfood_events");
        assertThat(NameMapper.tableName("fastfood", new DdlOptions("", true, false, false, true), 63))
                .isEqualTo("fastfood_events");
    }
}
