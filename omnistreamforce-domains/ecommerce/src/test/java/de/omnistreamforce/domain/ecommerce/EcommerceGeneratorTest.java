package de.omnistreamforce.domain.ecommerce;

import de.omnistreamforce.core.Event;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EcommerceGeneratorTest {

    private final EcommerceGenerator generator = new EcommerceGenerator();

    @Test
    void schemaIsComplete() {
        assertThat(generator.getDomainName()).isEqualTo("ecommerce");
        assertThat(generator.getSupportedEventTypes()).hasSize(5);
        assertThat(generator.getErrorTypes()).hasSize(4);
        assertThat(generator.getSchema().eventTypes()).hasSize(9);
        assertThat(generator.getSchema().errorSpec().errorRate()).isEqualTo(0.15);
    }

    @RepeatedTest(5)
    void generatesOrderCreatedWithRequiredFields() {
        Event event = generator.generateEvent("OrderCreated");
        assertThat(event.eventType()).isEqualTo("NORMAL");
        assertThat(event.domain()).isEqualTo("ecommerce");
        assertThat(event.payload()).containsKeys("orderId", "customerId", "customerName",
                "products", "totalAmount", "currency", "paymentMethod", "status");
        assertThat((List<?>) event.payload().get("products")).isNotEmpty();
        assertThat(event.payload().get("status")).isEqualTo("PENDING");
    }

    @Test
    void generatesEachNormalEventType() {
        for (String type : generator.getSupportedEventTypes()) {
            Event event = generator.generateEvent(type);
            assertThat(event.eventType()).isEqualTo("NORMAL");
            assertThat(event.payload()).containsKey("orderId");
        }
    }

    @RepeatedTest(5)
    void generatesErrorEventsWithMetadata() {
        Event event = generator.generateErrorEvent();
        assertThat(event.eventType()).isEqualTo("ERROR");
        assertThat(event.metadata()).containsKeys("errorType", "errorCode", "errorMessage", "severity");
        assertThat(generator.getErrorTypes()).contains(event.metadata().get("errorType"));
    }

    @Test
    void fraudDetectedUsesFraudHoldStatus() {
        Event event = generator.generateEvent(EcommerceGenerator.ERR_FRAUD_DETECTED);
        assertThat(event.metadata().get("errorType")).isEqualTo("FraudDetected");
        assertThat(event.payload().get("status")).isEqualTo("FRAUD_HOLD");
        Map<String, Object> payload = event.payload();
        double total = ((Number) payload.get("totalAmount")).doubleValue();
        assertThat(total).isGreaterThanOrEqualTo(5000.0);
    }

    @Test
    void unsupportedEventTypeThrows() {
        assertThatThrownBy(() -> generator.generateEvent("Whatever")).isInstanceOf(IllegalArgumentException.class);
    }
}