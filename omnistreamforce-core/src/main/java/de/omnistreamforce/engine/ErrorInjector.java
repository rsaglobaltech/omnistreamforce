package de.omnistreamforce.engine;

import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.Severity;
import de.omnistreamforce.domain.DomainGenerator;
import de.omnistreamforce.util.RandomUtils;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Inyeccion de errores controlada.
 * <p>
 * Decide que eventos seran errores segun la tasa configurada (0-100%),
 * asegura que todos los tipos de error del dominio se generen en algun momento y
 * permite configurar la distribucion de severidad (LOW/MEDIUM/HIGH/CRITICAL).
 * <p>
 * Thread-safe: {@link MultiDomainEngine} comparte una sola instancia entre todos los
 * dominios activos, cada uno con su propio hilo de generacion.
 */
public class ErrorInjector {

    /** Se reemplaza entero (copy-on-write) para que los lectores nunca vean un mapa a medias. */
    private volatile Map<Severity, Double> severityDistribution = defaultDistribution();
    private final Set<String> generatedErrorTypes = ConcurrentHashMap.newKeySet();

    public ErrorInjector() {
    }

    private static Map<Severity, Double> defaultDistribution() {
        Map<Severity, Double> distribution = new EnumMap<>(Severity.class);
        distribution.put(Severity.LOW, 0.4);
        distribution.put(Severity.MEDIUM, 0.35);
        distribution.put(Severity.HIGH, 0.2);
        distribution.put(Severity.CRITICAL, 0.05);
        return Collections.unmodifiableMap(distribution);
    }

    public void setSeverityDistribution(Map<Severity, Double> distribution) {
        if (distribution != null && !distribution.isEmpty()) {
            severityDistribution = Collections.unmodifiableMap(new EnumMap<>(distribution));
        }
    }

    /**
     * Decide si el siguiente evento debe ser un error segun la tasa (porcentaje 0-100).
     */
    public boolean shouldGenerateError(double errorRate) {
        return RandomUtils.chance(errorRate / 100.0);
    }

    /**
     * Selecciona el tipo de error a generar asegurando cobertura de todos los tipos.
     */
    public String nextErrorType(DomainGenerator generator) {
        List<String> errorTypes = generator.getErrorTypes();
        if (errorTypes == null || errorTypes.isEmpty()) {
            throw new IllegalStateException("El dominio no define tipos de error");
        }
        Set<String> unseen = new HashSet<>(errorTypes);
        unseen.removeAll(generatedErrorTypes);
        String chosen;
        if (!unseen.isEmpty() && RandomUtils.chance(0.4)) {
            chosen = RandomUtils.randomFrom(List.copyOf(unseen));
        } else {
            chosen = RandomUtils.randomFrom(errorTypes);
        }
        generatedErrorTypes.add(chosen);
        return chosen;
    }

    /**
     * Reparte una severidad segun la distribucion configurada.
     */
    public Severity nextSeverity() {
        Map<Severity, Double> distribution = severityDistribution;
        double r = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;
        Severity result = Severity.LOW;
        for (Map.Entry<Severity, Double> entry : distribution.entrySet()) {
            cumulative += entry.getValue();
            if (r < cumulative) {
                return entry.getKey();
            }
            result = entry.getKey();
        }
        return result;
    }

    public boolean allErrorTypesGenerated(DomainGenerator generator) {
        return generatedErrorTypes.containsAll(generator.getErrorTypes());
    }

    /**
     * Reconstruye el evento aplicando la severidad indicada a los metadatos.
     * <p>
     * Si el dominio ya fijo una severidad, se respeta: el generador sabe que una rotura de la
     * cadena de frio o un fallo de frenos son criticos, y la distribucion configurada no debe
     * degradarlos a LOW. La distribucion solo decide cuando el dominio no se pronuncia.
     */
    public static Event withSeverity(Event event, Severity severity) {
        if (severity == null) {
            return event;
        }
        java.util.Map<String, String> metadata = new java.util.LinkedHashMap<>(event.metadata());
        String existing = metadata.get("severity");
        if (existing != null && !existing.isBlank()) {
            return event;
        }
        metadata.put("severity", severity.name());
        return new Event(
                event.eventId(), event.eventType(), event.domain(), event.source(), event.timestamp(),
                event.schemaVersion(), event.payload(), metadata, event.traceId(), event.correlationId()
        );
    }
}