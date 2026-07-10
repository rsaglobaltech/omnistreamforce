package de.omnistreamforce.domain.healthcare;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.EventSpec;
import de.omnistreamforce.core.FieldDefinition;
import de.omnistreamforce.core.Severity;
import de.omnistreamforce.domain.AbstractDomainGenerator;
import de.omnistreamforce.util.RandomUtils;
import net.datafaker.Faker;

import java.util.List;
import java.util.Map;

public class HealthcareGenerator extends AbstractDomainGenerator {

    public static final String DOMAIN = "healthcare";

    public static final String PATIENT_ADMISSION = "PatientAdmission";
    public static final String VITAL_SIGNS_RECORDED = "VitalSignsRecorded";
    public static final String MEDICATION_ADMINISTERED = "MedicationAdministered";
    public static final String LAB_RESULT_READY = "LabResultReady";
    public static final String PATIENT_DISCHARGED = "PatientDischarged";

    public static final String ERR_PATIENT_ID_MISMATCH = "PatientIdMismatch";
    public static final String ERR_CRITICAL_VITAL_SIGNS = "CriticalVitalSigns";
    public static final String ERR_MEDICATION_CONFLICT = "MedicationConflict";
    public static final String ERR_LAB_RESULT_ANOMALY = "LabResultAnomaly";

    private static final List<String> NORMAL_TYPES = List.of(
            PATIENT_ADMISSION, VITAL_SIGNS_RECORDED, MEDICATION_ADMINISTERED,
            LAB_RESULT_READY, PATIENT_DISCHARGED);

    private static final List<String> ERROR_TYPES = List.of(
            ERR_PATIENT_ID_MISMATCH, ERR_CRITICAL_VITAL_SIGNS,
            ERR_MEDICATION_CONFLICT, ERR_LAB_RESULT_ANOMALY);

    private static final List<String> ADMISSION_TYPES = List.of("EMERGENCY", "ELECTIVE", "URGENT", "NEWBORN");
    private static final List<String> GENDERS = List.of("M", "F", "X");
    private static final List<String> MEDICATIONS = List.of(
            "Paracetamol", "Ibuprofen", "Amoxicillin", "Atorvastatin", "Metformin", "Aspirin");

    private final Faker faker = new Faker();

    @Override
    public String getDomainName() {
        return DOMAIN;
    }

    @Override
    public List<String> getSupportedEventTypes() {
        return NORMAL_TYPES;
    }

    @Override
    public List<String> getErrorTypes() {
        return ERROR_TYPES;
    }

    @Override
    public EventSchema getSchema() {
        FieldDefinition patientId = fd("patientId", "string", true, "regex PAT-[0-9]{6}", "Identificador del paciente");
        FieldDefinition patientName = fd("patientName", "string", true, "Name.fullName", "Nombre del paciente");
        FieldDefinition age = fd("age", "int", true, "number.numberBetween(0,110)", "Edad del paciente");
        FieldDefinition gender = fd("gender", "enum", true, null, GENDERS, "Genero");
        FieldDefinition admissionType = fd("admissionType", "enum", true, null, ADMISSION_TYPES, "Tipo de admision");

        List<FieldDefinition> commonFields = List.of(
                patientId, patientName, age, gender, admissionType,
                fd("heartRate", "int", false, "number.numberBetween(40,200)", "Frecuencia cardiaca"),
                fd("bloodPressure", "string", false, " medicallyPlausible bp", "Presion arterial"),
                fd("temperature", "double", false, "number.numberBetween(34.0,41.0)", "Temperatura"),
                fd("oxygenSaturation", "double", false, "number.numberBetween(80.0,100.0)", "Saturacion de oxigeno"),
                fd("medications", "array", false, null, "Medicamentos administrados"),
                fd("labResults", "object", false, null, "Resultados de laboratorio")
        );

        return new EventSchema(
                DOMAIN,
                "HealthcareEvents",
                "Eventos del dominio de salud: admisiones, signos vitales, medicacion, resultados de laboratorio y alta.",
                commonFields,
                List.of(
                        new EventSpec(PATIENT_ADMISSION, de.omnistreamforce.core.EventType.NORMAL,
                                "Paciente admitido en el centro", commonFields),
                        new EventSpec(VITAL_SIGNS_RECORDED, de.omnistreamforce.core.EventType.NORMAL,
                                "Registro de signos vitales", commonFields),
                        new EventSpec(MEDICATION_ADMINISTERED, de.omnistreamforce.core.EventType.NORMAL,
                                "Administracion de medicacion", commonFields),
                        new EventSpec(LAB_RESULT_READY, de.omnistreamforce.core.EventType.NORMAL,
                                "Resultado de laboratorio disponible", commonFields),
                        new EventSpec(PATIENT_DISCHARGED, de.omnistreamforce.core.EventType.NORMAL,
                                "Paciente dado de alta", commonFields),
                        new EventSpec(ERR_PATIENT_ID_MISMATCH, de.omnistreamforce.core.EventType.ERROR,
                                "Discrepancia en el identificador del paciente", commonFields),
                        new EventSpec(ERR_CRITICAL_VITAL_SIGNS, de.omnistreamforce.core.EventType.ERROR,
                                "Signos vitales en rango critico", commonFields),
                        new EventSpec(ERR_MEDICATION_CONFLICT, de.omnistreamforce.core.EventType.ERROR,
                                "Conflicto de medicacion detectado", commonFields),
                        new EventSpec(ERR_LAB_RESULT_ANOMALY, de.omnistreamforce.core.EventType.ERROR,
                                "Anomalia en resultado de laboratorio", commonFields)
                ),
                new de.omnistreamforce.core.ErrorSpec(ERROR_TYPES, 0.10,
                        List.of("patientId", "heartRate", "temperature", "oxygenSaturation", "medications"))
        );
    }

    @Override
    public Event generateEvent(String eventType) {
        return switch (eventType) {
            case PATIENT_ADMISSION -> patientAdmission();
            case VITAL_SIGNS_RECORDED -> vitalSignsRecorded();
            case MEDICATION_ADMINISTERED -> medicationAdministered();
            case LAB_RESULT_READY -> labResultReady();
            case PATIENT_DISCHARGED -> patientDischarged();
            case ERR_PATIENT_ID_MISMATCH, ERR_CRITICAL_VITAL_SIGNS,
                 ERR_MEDICATION_CONFLICT, ERR_LAB_RESULT_ANOMALY -> generateErrorEvent(eventType);
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + eventType);
        };
    }

    @Override
    public Event generateErrorEvent() {
        return generateErrorEvent(RandomUtils.randomFrom(ERROR_TYPES));
    }

    private Event generateErrorEvent(String errorType) {
        String patientId = "PAT-" + RandomUtils.nextInt(100000, 999999);
        Map<String, Object> payload = payload(
                "patientId", patientId,
                "patientName", faker.name().fullName(),
                "age", RandomUtils.nextInt(0, 110),
                "gender", RandomUtils.randomFrom(GENDERS),
                "admissionType", RandomUtils.randomFrom(ADMISSION_TYPES)
        );
        return switch (errorType) {
            case ERR_PATIENT_ID_MISMATCH ->
                    buildErrorEvent(DOMAIN, ERR_PATIENT_ID_MISMATCH,
                            "El identificador del paciente no coincide con el registro",
                            Severity.HIGH, withBrokenPatientId(payload));
            case ERR_CRITICAL_VITAL_SIGNS ->
                    buildErrorEvent(DOMAIN, ERR_CRITICAL_VITAL_SIGNS,
                            "Signos vitales fuera del rango critico",
                            Severity.CRITICAL, withCriticalVitals(payload));
            case ERR_MEDICATION_CONFLICT ->
                    buildErrorEvent(DOMAIN, ERR_MEDICATION_CONFLICT,
                            "Conflicto entre medicamentos prescritos",
                            Severity.HIGH, withMedicationConflict(payload));
            case ERR_LAB_RESULT_ANOMALY ->
                    buildErrorEvent(DOMAIN, ERR_LAB_RESULT_ANOMALY,
                            "Resultado de laboratorio presenta valores anomalos",
                            Severity.MEDIUM, withLabAnomaly(payload));
            default -> throw new IllegalArgumentException("Tipo de error no soportado: " + errorType);
        };
    }

    private Event patientAdmission() {
        Map<String, Object> payload = commonPatientPayload();
        payload.put("vitalSigns", vitalSigns());
        return buildNormalEvent(DOMAIN, PATIENT_ADMISSION, payload);
    }

    private Event vitalSignsRecorded() {
        Map<String, Object> payload = commonPatientPayload();
        payload.put("vitalSigns", vitalSigns());
        return buildNormalEvent(DOMAIN, VITAL_SIGNS_RECORDED, payload);
    }

    private Event medicationAdministered() {
        Map<String, Object> payload = commonPatientPayload();
        payload.put("medications", List.of(
                Map.of("name", RandomUtils.randomFrom(MEDICATIONS), "doseMg", RandomUtils.nextInt(50, 1000),
                        "route", RandomUtils.randomFrom("ORAL", "IV", "IM")),
                Map.of("name", RandomUtils.randomFrom(MEDICATIONS), "doseMg", RandomUtils.nextInt(50, 1000),
                        "route", RandomUtils.randomFrom("ORAL", "IV", "IM"))
        ));
        return buildNormalEvent(DOMAIN, MEDICATION_ADMINISTERED, payload);
    }

    private Event labResultReady() {
        Map<String, Object> payload = commonPatientPayload();
        payload.put("labResults", Map.of(
                "test", RandomUtils.randomFrom("GLUCOSE", "HEMOGLOBIN", "CHOLESTEROL", "WBC"),
                "value", RandomUtils.nextDouble(40.0, 250.0),
                "unit", RandomUtils.randomFrom("mg/dL", "g/dL", "mmol/L"),
                "status", "READY"
        ));
        return buildNormalEvent(DOMAIN, LAB_RESULT_READY, payload);
    }

    private Event patientDischarged() {
        Map<String, Object> payload = commonPatientPayload();
        payload.put("dischargeReason", RandomUtils.randomFrom("RECOVERED", "TRANSFERRED", "AMA"));
        payload.put("stayDays", RandomUtils.nextInt(1, 30));
        return buildNormalEvent(DOMAIN, PATIENT_DISCHARGED, payload);
    }

    private Map<String, Object> commonPatientPayload() {
        return payload(
                "patientId", "PAT-" + RandomUtils.nextInt(100000, 999999),
                "patientName", faker.name().fullName(),
                "age", RandomUtils.nextInt(0, 110),
                "gender", RandomUtils.randomFrom(GENDERS),
                "admissionType", RandomUtils.randomFrom(ADMISSION_TYPES)
        );
    }

    private Map<String, Object> vitalSigns() {
        return Map.of(
                "heartRate", RandomUtils.nextInt(50, 120),
                "bloodPressure", RandomUtils.nextInt(90, 140) + "/" + RandomUtils.nextInt(60, 90),
                "temperature", Math.round(RandomUtils.nextDouble(36.0, 38.5) * 10) / 10.0,
                "oxygenSaturation", Math.round(RandomUtils.nextDouble(95.0, 100.0) * 10) / 10.0
        );
    }

    private Map<String, Object> withBrokenPatientId(Map<String, Object> payload) {
        payload.put("patientId", RandomUtils.chance(0.5) ? null : "INVALID-ID-" + faker.lorem().characters(3));
        return payload;
    }

    private Map<String, Object> withCriticalVitals(Map<String, Object> payload) {
        payload.put("vitalSigns", Map.of(
                "heartRate", RandomUtils.randomFrom(35, 210),
                "bloodPressure", "180/120",
                "temperature", RandomUtils.nextDouble(39.5, 42.0),
                "oxygenSaturation", RandomUtils.nextDouble(70.0, 85.0)
        ));
        return payload;
    }

    private Map<String, Object> withMedicationConflict(Map<String, Object> payload) {
        payload.put("medications", List.of(
                Map.of("name", "Warfarin", "doseMg", 5, "route", "ORAL"),
                Map.of("name", "Aspirin", "doseMg", 325, "route", "ORAL")
        ));
        return payload;
    }

    private Map<String, Object> withLabAnomaly(Map<String, Object> payload) {
        payload.put("labResults", Map.of(
                "test", "GLUCOSE",
                "value", RandomUtils.randomFrom(350.0, 5.0, 620.0),
                "unit", "mg/dL",
                "status", "ANOMALY"
        ));
        return payload;
    }

    private FieldDefinition fd(String name, String type, boolean required, String fakerExpression, String description) {
        return new FieldDefinition(name, type, required, fakerExpression, null, null, description);
    }

    private FieldDefinition fd(String name, String type, boolean required, String fakerExpression,
                               List<String> enumValues, String description) {
        return new FieldDefinition(name, type, required, fakerExpression, null, enumValues, description);
    }
}