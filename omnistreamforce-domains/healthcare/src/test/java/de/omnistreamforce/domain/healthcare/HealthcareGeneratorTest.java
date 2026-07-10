package de.omnistreamforce.domain.healthcare;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventType;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthcareGeneratorTest {

    private final HealthcareGenerator generator = new HealthcareGenerator();

    @Test
    void schemaIsComplete() {
        assertThat(generator.getDomainName()).isEqualTo("healthcare");
        assertThat(generator.getSupportedEventTypes()).hasSize(5);
        assertThat(generator.getErrorTypes()).hasSize(4);
        assertThat(generator.getSchema().domain()).isEqualTo("healthcare");
        assertThat(generator.getSchema().eventTypes()).hasSize(9);
    }

    @Test
    void supportsNormalEventTypes() {
        assertThat(generator.getSupportedEventTypes())
                .contains("PatientAdmission", "VitalSignsRecorded", "MedicationAdministered",
                        "LabResultReady", "PatientDischarged");
    }

    @RepeatedTest(5)
    void generatesNormalEventWithRequiredFields() {
        Event event = generator.generateEvent("PatientAdmission");
        assertThat(event.eventType()).isEqualTo("NORMAL");
        assertThat(event.domain()).isEqualTo("healthcare");
        assertThat(event.payload()).containsKeys("patientId", "patientName", "age", "gender", "admissionType");
        assertThat(event.payload().get("vitalSigns")).isInstanceOf(java.util.Map.class);
        assertThat(event.traceId()).isNotBlank();
        assertThat(event.correlationId()).isNotBlank();
    }

    @RepeatedTest(5)
    void generatesErrorEventsWithSeverityMetadata() {
        Event event = generator.generateErrorEvent();
        assertThat(event.eventType()).isEqualTo("ERROR");
        assertThat(event.metadata()).containsKey("severity");
        assertThat(event.metadata()).containsKeys("errorType", "errorCode", "errorMessage");
        assertThat(List.of(HealthcareGenerator.ERR_PATIENT_ID_MISMATCH,
                HealthcareGenerator.ERR_CRITICAL_VITAL_SIGNS,
                HealthcareGenerator.ERR_MEDICATION_CONFLICT,
                HealthcareGenerator.ERR_LAB_RESULT_ANOMALY))
                .contains(event.metadata().get("errorType"));
    }

    @Test
    void generatesSpecificErrorEvent() {
        Event event = generator.generateEvent(HealthcareGenerator.ERR_MEDICATION_CONFLICT);
        assertThat(event.eventType()).isEqualTo("ERROR");
        assertThat(event.metadata().get("errorType")).isEqualTo("MedicationConflict");
    }

    @Test
    void unsupportedEventTypeThrows() {
        assertThatThrownBy(() -> generator.generateEvent("UnknownEvent"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void patientVitalSignsAreInHealthyRangeForNormalEvents() {
        for (int i = 0; i < 20; i++) {
            Event event = generator.generateEvent("VitalSignsRecorded");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> vitals = (java.util.Map<String, Object>) event.payload().get("vitalSigns");
            int heartRate = ((Number) vitals.get("heartRate")).intValue();
            assertThat(heartRate).isBetween(50, 120);
        }
    }
}