package de.omnistreamforce.domain.ecommerce;

import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.EventSpec;
import de.omnistreamforce.core.FieldDefinition;
import de.omnistreamforce.core.Severity;
import de.omnistreamforce.domain.AbstractDomainGenerator;
import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.ErrorSpec;
import de.omnistreamforce.core.EventType;
import de.omnistreamforce.util.RandomUtils;
import net.datafaker.Faker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EcommerceGenerator extends AbstractDomainGenerator {

    public static final String DOMAIN = "ecommerce";

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String PAYMENT_PROCESSED = "PaymentProcessed";
    public static final String INVENTORY_UPDATED = "InventoryUpdated";
    public static final String ORDER_SHIPPED = "OrderShipped";
    public static final String ORDER_CANCELLED = "OrderCancelled";

    public static final String ERR_PAYMENT_FAILED = "PaymentFailed";
    public static final String ERR_INVENTORY_OUT_OF_STOCK = "InventoryOutOfStock";
    public static final String ERR_FRAUD_DETECTED = "FraudDetected";
    public static final String ERR_SHIPPING_FAILED = "ShippingFailed";

    private static final List<String> NORMAL_TYPES = List.of(
            ORDER_CREATED, PAYMENT_PROCESSED, INVENTORY_UPDATED, ORDER_SHIPPED, ORDER_CANCELLED);

    private static final List<String> ERROR_TYPES = List.of(
            ERR_PAYMENT_FAILED, ERR_INVENTORY_OUT_OF_STOCK, ERR_FRAUD_DETECTED, ERR_SHIPPING_FAILED);

    private static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP", "JPY");
    private static final List<String> PAYMENT_METHODS = List.of("CREDIT_CARD", "DEBIT_CARD", "PAYPAL", "BANK_TRANSFER", "CRYPTO");
    private static final List<String> ORDER_STATUS = List.of("PENDING", "PAID", "FULFILLED", "SHIPPED", "CANCELLED", "REFUNDED");

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
        List<FieldDefinition> fields = List.of(
                fd("orderId", "string", true, "regex ORD-[0-9]{8}", "Identificador del pedido"),
                fd("customerId", "string", true, "regex CUST-[0-9]{6}", "Identificador del cliente"),
                fd("customerName", "string", true, "Name.fullName", "Nombre del cliente"),
                fd("products", "array", true, null, "Lista de productos del pedido"),
                fd("totalAmount", "double", true, "number.numberBetween(5.0,5000.0)", "Importe total"),
                fd("currency", "enum", true, null, CURRENCIES, "Moneda"),
                fd("paymentMethod", "enum", true, null, PAYMENT_METHODS, "Metodo de pago"),
                fd("shippingAddress", "string", false, "Address.fullAddress", "Direccion de envio"),
                fd("status", "enum", true, null, ORDER_STATUS, "Estado del pedido")
        );

        return new EventSchema(
                DOMAIN,
                "EcommerceEvents",
                "Eventos del dominio de ecommerce: pedidos, pagos, inventario y envios.",
                fields,
                List.of(
                        new EventSpec(ORDER_CREATED, EventType.NORMAL, "Pedido creado", fields),
                        new EventSpec(PAYMENT_PROCESSED, EventType.NORMAL, "Pago procesado", fields),
                        new EventSpec(INVENTORY_UPDATED, EventType.NORMAL, "Inventario actualizado", fields),
                        new EventSpec(ORDER_SHIPPED, EventType.NORMAL, "Pedido enviado", fields),
                        new EventSpec(ORDER_CANCELLED, EventType.NORMAL, "Pedido cancelado", fields),
                        new EventSpec(ERR_PAYMENT_FAILED, EventType.ERROR, "Pago rechazado", fields),
                        new EventSpec(ERR_INVENTORY_OUT_OF_STOCK, EventType.ERROR, "Producto sin stock", fields),
                        new EventSpec(ERR_FRAUD_DETECTED, EventType.ERROR, "Fraude detectado", fields),
                        new EventSpec(ERR_SHIPPING_FAILED, EventType.ERROR, "Envio fallido", fields)
                ),
                new ErrorSpec(ERROR_TYPES, 0.15,
                        List.of("paymentMethod", "totalAmount", "products", "status", "shippingAddress"))
        );
    }

    @Override
    public Event generateEvent(String eventType) {
        return switch (eventType) {
            case ORDER_CREATED -> orderCreated();
            case PAYMENT_PROCESSED -> paymentProcessed();
            case INVENTORY_UPDATED -> inventoryUpdated();
            case ORDER_SHIPPED -> orderShipped();
            case ORDER_CANCELLED -> orderCancelled();
            case ERR_PAYMENT_FAILED, ERR_INVENTORY_OUT_OF_STOCK,
                 ERR_FRAUD_DETECTED, ERR_SHIPPING_FAILED -> generateErrorEvent(eventType);
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + eventType);
        };
    }

    @Override
    public Event generateErrorEvent() {
        return generateErrorEvent(RandomUtils.randomFrom(ERROR_TYPES));
    }

    private Event generateErrorEvent(String errorType) {
        Map<String, Object> payload = orderBasePayload();
        return switch (errorType) {
            case ERR_PAYMENT_FAILED -> buildErrorEvent(DOMAIN, ERR_PAYMENT_FAILED,
                    "Pago rechazado por el proveedor de pagos",
                    Severity.HIGH, markStatus(payload, "CANCELLED"));
            case ERR_INVENTORY_OUT_OF_STOCK -> buildErrorEvent(DOMAIN, ERR_INVENTORY_OUT_OF_STOCK,
                    "Producto sin stock disponible",
                    Severity.MEDIUM, outOfStockPayload(payload));
            case ERR_FRAUD_DETECTED -> buildErrorEvent(DOMAIN, ERR_FRAUD_DETECTED,
                    "Patron de fraude detectado en el pedido",
                    Severity.CRITICAL, fraudPayload(payload));
            case ERR_SHIPPING_FAILED -> buildErrorEvent(DOMAIN, ERR_SHIPPING_FAILED,
                    "Envio fallido: direccion invalida o transportista no disponible",
                    Severity.MEDIUM, markStatus(payload, "CANCELLED"));
            default -> throw new IllegalArgumentException("Tipo de error no soportado: " + errorType);
        };
    }

    private Event orderCreated() {
        double total = totalAmount();
        Map<String, Object> payload = orderBasePayload(total);
        payload.put("status", "PENDING");
        return buildNormalEvent(DOMAIN, ORDER_CREATED, payload);
    }

    private Event paymentProcessed() {
        Map<String, Object> payload = orderBasePayload();
        payload.put("status", "PAID");
        return buildNormalEvent(DOMAIN, PAYMENT_PROCESSED, payload);
    }

    private Event inventoryUpdated() {
        Map<String, Object> payload = orderBasePayload();
        payload.put("status", "FULFILLED");
        return buildNormalEvent(DOMAIN, INVENTORY_UPDATED, payload);
    }

    private Event orderShipped() {
        Map<String, Object> payload = orderBasePayload();
        payload.put("status", "SHIPPED");
        payload.put("trackingNumber", "TRK" + RandomUtils.nextInt(1000000, 9999999));
        return buildNormalEvent(DOMAIN, ORDER_SHIPPED, payload);
    }

    private Event orderCancelled() {
        Map<String, Object> payload = orderBasePayload();
        payload.put("status", "CANCELLED");
        payload.put("cancelReason", RandomUtils.randomFrom("CUSTOMER_REQUEST", "OUT_OF_STOCK", "PAYMENT_FAILED"));
        return buildNormalEvent(DOMAIN, ORDER_CANCELLED, payload);
    }

    private Map<String, Object> orderBasePayload() {
        return orderBasePayload(totalAmount());
    }

    private Map<String, Object> orderBasePayload(double total) {
        Map<String, Object> payload = payload(
                "orderId", "ORD-" + RandomUtils.nextInt(10000000, 99999999),
                "customerId", "CUST-" + RandomUtils.nextInt(100000, 999999),
                "customerName", faker.name().fullName(),
                "products", products(),
                "totalAmount", Math.round(total * 100.0) / 100.0,
                "currency", RandomUtils.randomFrom(CURRENCIES),
                "paymentMethod", RandomUtils.randomFrom(PAYMENT_METHODS),
                "shippingAddress", faker.address().fullAddress()
        );
        return payload;
    }

    private List<Map<String, Object>> products() {
        List<Map<String, Object>> products = new ArrayList<>();
        int count = RandomUtils.nextInt(1, 5);
        for (int i = 0; i < count; i++) {
            products.add(Map.of(
                    "productId", "SKU-" + RandomUtils.nextInt(1000, 9999),
                    "name", faker.commerce().productName(),
                    "quantity", RandomUtils.nextInt(1, 10),
                    "unitPrice", Math.round(RandomUtils.nextDouble(5.0, 500.0) * 100.0) / 100.0
            ));
        }
        return products;
    }

    private double totalAmount() {
        return RandomUtils.nextDouble(5.0, 5000.0);
    }

    private Map<String, Object> markStatus(Map<String, Object> payload, String status) {
        payload.put("status", status);
        return payload;
    }

    private Map<String, Object> outOfStockPayload(Map<String, Object> payload) {
        payload.put("products", List.of(Map.of(
                "productId", "SKU-" + RandomUtils.nextInt(1000, 9999),
                "name", faker.commerce().productName(),
                "quantity", RandomUtils.nextInt(1, 10),
                "unitPrice", 0.0,
                "available", false
        )));
        payload.put("status", "CANCELLED");
        return payload;
    }

    private Map<String, Object> fraudPayload(Map<String, Object> payload) {
        payload.put("status", "FRAUD_HOLD");
        payload.put("totalAmount", Math.round(RandomUtils.nextDouble(5000.0, 50000.0) * 100.0) / 100.0);
        payload.put("paymentMethod", "CRYPTO");
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