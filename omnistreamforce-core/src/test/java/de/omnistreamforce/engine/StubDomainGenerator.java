package de.omnistreamforce.engine;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.EventSpec;
import de.omnistreamforce.core.EventType;
import de.omnistreamforce.core.FieldDefinition;
import de.omnistreamforce.core.ErrorSpec;
import de.omnistreamforce.domain.AbstractDomainGenerator;
import de.omnistreamforce.util.RandomUtils;

import java.util.List;
import java.util.Map;

/**
 * Generador de prueba independiente de los modulos de dominio.
 */
public class StubDomainGenerator extends AbstractDomainGenerator {

    public static final String DOMAIN = "stub";
    public static final String NORMAL = "NormalEvent";
    public static final String ERR_A = "FaultA";
    public static final String ERR_B = "FaultB";
    public static final String ERR_C = "FaultC";
    public static final String ERR_D = "FaultD";

    private static final List<String> NORMAL_TYPES = List.of(NORMAL);
    private static final List<String> ERROR_TYPES = List.of(ERR_A, ERR_B, ERR_C, ERR_D);

    private final String domain;

    public StubDomainGenerator() {
        this(DOMAIN);
    }

    public StubDomainGenerator(String domain) {
        this.domain = domain;
    }

    @Override
    public String getDomainName() {
        return domain;
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
        return new EventSchema(domain, "StubEvents", "Generador de prueba",
                List.of(new FieldDefinition("entityId", "string", true, "regex STUB-[0-9]{4}", null, null, "Id")),
                List.of(
                        new EventSpec(NORMAL, EventType.NORMAL, "Evento normal", List.of()),
                        new EventSpec(ERR_A, EventType.ERROR, "Falla A", List.of()),
                        new EventSpec(ERR_B, EventType.ERROR, "Falla B", List.of()),
                        new EventSpec(ERR_C, EventType.ERROR, "Falla C", List.of()),
                        new EventSpec(ERR_D, EventType.ERROR, "Falla D", List.of())),
                new ErrorSpec(ERROR_TYPES, 0.10, List.of("entityId")));
    }

    @Override
    public Event generateEvent(String eventType) {
        if (ERROR_TYPES.contains(eventType)) {
            return generateErrorEvent(eventType);
        }
        Map<String, Object> payload = payload("entityId", "STUB-" + RandomUtils.nextInt(1000, 9999));
        return buildNormalEvent(domain, NORMAL, payload);
    }

    @Override
    public Event generateErrorEvent() {
        return generateErrorEvent(RandomUtils.randomFrom(ERROR_TYPES));
    }

    private Event generateErrorEvent(String errorType) {
        Map<String, Object> payload = payload("entityId", "STUB-" + RandomUtils.nextInt(1000, 9999));
        return buildErrorEvent(domain, errorType, "Error simulado: " + errorType,
                de.omnistreamforce.core.Severity.HIGH, payload);
    }
}