package de.omnistreamforce.domain.fastfood;

import de.omnistreamforce.core.ErrorSpec;
import de.omnistreamforce.core.Event;
import de.omnistreamforce.core.EventSchema;
import de.omnistreamforce.core.EventSpec;
import de.omnistreamforce.core.EventType;
import de.omnistreamforce.core.FieldDefinition;
import de.omnistreamforce.core.Severity;
import de.omnistreamforce.domain.AbstractDomainGenerator;
import de.omnistreamforce.domain.fastfood.FastFoodBrand.Ingredient;
import de.omnistreamforce.domain.fastfood.FastFoodBrand.MenuItem;
import de.omnistreamforce.util.RandomUtils;
import net.datafaker.Faker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dominio de franquicias de comida rapida (Burger King, McDonald's, ...).
 * La franquicia concreta viaja en el payload como campo {@code brand}, de modo
 * que todas las marcas comparten el mismo topic y anadir una nueva solo requiere
 * una constante en {@link FastFoodBrand}.
 */
public class FastFoodGenerator extends AbstractDomainGenerator {

    public static final String DOMAIN = "fastfood";

    // Pedidos y cocina
    public static final String ORDER_PLACED = "OrderPlaced";
    public static final String ORDER_PAID = "OrderPaid";
    public static final String KITCHEN_PREP_STARTED = "KitchenPrepStarted";
    public static final String ORDER_READY = "OrderReady";
    public static final String ORDER_DELIVERED = "OrderDelivered";

    // Drive-thru y canales
    public static final String DRIVE_THRU_ARRIVAL = "DriveThruArrival";
    public static final String DRIVE_THRU_ORDER = "DriveThruOrder";
    public static final String KIOSK_ORDER = "KioskOrder";
    public static final String APP_ORDER = "AppOrder";
    public static final String DELIVERY_DISPATCHED = "DeliveryDispatched";

    // Inventario de tienda
    public static final String INVENTORY_UPDATED = "InventoryUpdated";
    public static final String STOCK_REPLENISHED = "StockReplenished";
    public static final String WASTE_RECORDED = "WasteRecorded";

    // Errores de pedido y cocina
    public static final String ERR_PAYMENT_DECLINED = "PaymentDeclined";
    public static final String ERR_ITEM_OUT_OF_STOCK = "ItemOutOfStock";
    public static final String ERR_KITCHEN_DELAY = "KitchenDelay";
    public static final String ERR_WRONG_ORDER_DELIVERED = "WrongOrderDelivered";

    // Errores de canal
    public static final String ERR_DRIVE_THRU_TIMEOUT = "DriveThruTimeout";
    public static final String ERR_DELIVERY_FAILED = "DeliveryFailed";

    // Errores de inventario
    public static final String ERR_INGREDIENT_OUT_OF_STOCK = "IngredientOutOfStock";
    public static final String ERR_COLD_CHAIN_BREACH = "ColdChainBreach";

    private static final List<String> NORMAL_TYPES = List.of(
            ORDER_PLACED, ORDER_PAID, KITCHEN_PREP_STARTED, ORDER_READY, ORDER_DELIVERED,
            DRIVE_THRU_ARRIVAL, DRIVE_THRU_ORDER, KIOSK_ORDER, APP_ORDER, DELIVERY_DISPATCHED,
            INVENTORY_UPDATED, STOCK_REPLENISHED, WASTE_RECORDED);

    private static final List<String> ERROR_TYPES = List.of(
            ERR_PAYMENT_DECLINED, ERR_ITEM_OUT_OF_STOCK, ERR_KITCHEN_DELAY, ERR_WRONG_ORDER_DELIVERED,
            ERR_DRIVE_THRU_TIMEOUT, ERR_DELIVERY_FAILED,
            ERR_INGREDIENT_OUT_OF_STOCK, ERR_COLD_CHAIN_BREACH);

    private static final List<String> CHANNELS = List.of("COUNTER", "DRIVE_THRU", "KIOSK", "MOBILE_APP", "DELIVERY");
    private static final List<String> PAYMENT_METHODS = List.of("CASH", "CREDIT_CARD", "DEBIT_CARD", "MOBILE_APP", "GIFT_CARD");
    private static final List<String> ORDER_STATUS = List.of("PLACED", "PAID", "IN_PREP", "READY", "DELIVERED", "CANCELLED");
    private static final List<String> CURRENCIES = List.of("USD", "EUR", "GBP");
    private static final List<String> DELIVERY_PARTNERS = List.of("UBER_EATS", "DELIVEROO", "JUST_EAT", "OWN_FLEET");

    /** SLA de preparacion en cocina: por encima de este valor se considera retraso. */
    private static final int PREP_SLA_SECONDS = 300;
    /** Temperatura maxima admitida en congelador antes de romper la cadena de frio. */
    private static final double COLD_CHAIN_THRESHOLD_CELSIUS = -18.0;

    private record Market(String country, String currency) {
    }

    private static final List<Market> MARKETS = List.of(
            new Market("US", "USD"),
            new Market("DE", "EUR"),
            new Market("ES", "EUR"),
            new Market("GB", "GBP"));

    private final Faker faker = new Faker();
    private final List<FastFoodBrand> brands;

    public FastFoodGenerator() {
        this(List.of(FastFoodBrand.values()));
    }

    /**
     * Permite restringir el generador a un subconjunto de franquicias
     * (por ejemplo, publicar solo eventos de Burger King).
     */
    public FastFoodGenerator(List<FastFoodBrand> brands) {
        if (brands == null || brands.isEmpty()) {
            throw new IllegalArgumentException("Se requiere al menos una franquicia");
        }
        this.brands = List.copyOf(brands);
    }

    public List<FastFoodBrand> getBrands() {
        return brands;
    }

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
                fd("brand", "enum", true, null, FastFoodBrand.names(), "Franquicia que emite el evento"),
                fd("brandName", "string", true, null, "Nombre comercial de la franquicia"),
                fd("storeId", "string", true, "regex [A-Z]{2,3}-[0-9]{4}", "Identificador de la tienda"),
                fd("storeCity", "string", true, "Address.city", "Ciudad de la tienda"),
                fd("country", "string", true, null, "Codigo de pais de la tienda"),
                fd("orderId", "string", false, "regex ORD-[A-Z]{2,3}-[0-9]{8}", "Identificador del pedido"),
                fd("channel", "enum", false, null, CHANNELS, "Canal de venta"),
                fd("items", "array", false, null, "Articulos del pedido"),
                fd("itemCount", "int", false, "number.numberBetween(1,6)", "Numero de articulos del pedido"),
                fd("totalAmount", "double", false, "number.numberBetween(2.0,80.0)", "Importe total del pedido"),
                fd("currency", "enum", false, null, CURRENCIES, "Moneda"),
                fd("paymentMethod", "enum", false, null, PAYMENT_METHODS, "Metodo de pago"),
                fd("orderStatus", "enum", false, null, ORDER_STATUS, "Estado del pedido"),
                fd("prepTimeSeconds", "int", false, "number.numberBetween(60,600)", "Tiempo de preparacion en cocina"),
                fd("employeeId", "string", false, "regex EMP-[0-9]{5}", "Empleado que atiende el pedido"),
                fd("ingredientSku", "string", false, null, "SKU del ingrediente en inventario"),
                fd("ingredientName", "string", false, null, "Nombre del ingrediente"),
                fd("quantityOnHand", "double", false, "number.numberBetween(0.0,400.0)", "Existencias disponibles"),
                fd("unit", "string", false, null, "Unidad de medida del ingrediente")
        );

        return new EventSchema(
                DOMAIN,
                "FastFoodEvents",
                "Eventos de franquicias de comida rapida (Burger King, McDonald's): pedidos, cocina, canales de venta e inventario de tienda.",
                fields,
                List.of(
                        new EventSpec(ORDER_PLACED, EventType.NORMAL, "Pedido registrado", fields),
                        new EventSpec(ORDER_PAID, EventType.NORMAL, "Pedido pagado", fields),
                        new EventSpec(KITCHEN_PREP_STARTED, EventType.NORMAL, "Cocina inicia la preparacion", fields),
                        new EventSpec(ORDER_READY, EventType.NORMAL, "Pedido listo para entregar", fields),
                        new EventSpec(ORDER_DELIVERED, EventType.NORMAL, "Pedido entregado al cliente", fields),
                        new EventSpec(DRIVE_THRU_ARRIVAL, EventType.NORMAL, "Vehiculo llega al drive-thru", fields),
                        new EventSpec(DRIVE_THRU_ORDER, EventType.NORMAL, "Pedido tomado en drive-thru", fields),
                        new EventSpec(KIOSK_ORDER, EventType.NORMAL, "Pedido realizado en kiosco", fields),
                        new EventSpec(APP_ORDER, EventType.NORMAL, "Pedido realizado desde la app", fields),
                        new EventSpec(DELIVERY_DISPATCHED, EventType.NORMAL, "Pedido despachado a reparto", fields),
                        new EventSpec(INVENTORY_UPDATED, EventType.NORMAL, "Inventario de tienda actualizado", fields),
                        new EventSpec(STOCK_REPLENISHED, EventType.NORMAL, "Reposicion de stock recibida", fields),
                        new EventSpec(WASTE_RECORDED, EventType.NORMAL, "Merma registrada", fields),
                        new EventSpec(ERR_PAYMENT_DECLINED, EventType.ERROR, "Pago rechazado", fields),
                        new EventSpec(ERR_ITEM_OUT_OF_STOCK, EventType.ERROR, "Articulo de la carta no disponible", fields),
                        new EventSpec(ERR_KITCHEN_DELAY, EventType.ERROR, "Retraso de cocina sobre el SLA", fields),
                        new EventSpec(ERR_WRONG_ORDER_DELIVERED, EventType.ERROR, "Pedido entregado equivocado", fields),
                        new EventSpec(ERR_DRIVE_THRU_TIMEOUT, EventType.ERROR, "Timeout en drive-thru", fields),
                        new EventSpec(ERR_DELIVERY_FAILED, EventType.ERROR, "Entrega a domicilio fallida", fields),
                        new EventSpec(ERR_INGREDIENT_OUT_OF_STOCK, EventType.ERROR, "Ingrediente agotado en tienda", fields),
                        new EventSpec(ERR_COLD_CHAIN_BREACH, EventType.ERROR, "Rotura de la cadena de frio", fields)
                ),
                new ErrorSpec(ERROR_TYPES, 0.12,
                        List.of("paymentMethod", "items", "orderStatus", "prepTimeSeconds", "quantityOnHand"))
        );
    }

    @Override
    public Event generateEvent(String eventType) {
        return switch (eventType) {
            case ORDER_PLACED -> orderPlaced();
            case ORDER_PAID -> orderPaid();
            case KITCHEN_PREP_STARTED -> kitchenPrepStarted();
            case ORDER_READY -> orderReady();
            case ORDER_DELIVERED -> orderDelivered();
            case DRIVE_THRU_ARRIVAL -> driveThruArrival();
            case DRIVE_THRU_ORDER -> driveThruOrder();
            case KIOSK_ORDER -> kioskOrder();
            case APP_ORDER -> appOrder();
            case DELIVERY_DISPATCHED -> deliveryDispatched();
            case INVENTORY_UPDATED -> inventoryUpdated();
            case STOCK_REPLENISHED -> stockReplenished();
            case WASTE_RECORDED -> wasteRecorded();
            case ERR_PAYMENT_DECLINED, ERR_ITEM_OUT_OF_STOCK, ERR_KITCHEN_DELAY, ERR_WRONG_ORDER_DELIVERED,
                 ERR_DRIVE_THRU_TIMEOUT, ERR_DELIVERY_FAILED,
                 ERR_INGREDIENT_OUT_OF_STOCK, ERR_COLD_CHAIN_BREACH -> generateErrorEvent(eventType);
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + eventType);
        };
    }

    @Override
    public Event generateErrorEvent() {
        return generateErrorEvent(RandomUtils.randomFrom(ERROR_TYPES));
    }

    private Event generateErrorEvent(String errorType) {
        FastFoodBrand brand = randomBrand();
        return switch (errorType) {
            case ERR_PAYMENT_DECLINED -> buildErrorEvent(DOMAIN, ERR_PAYMENT_DECLINED,
                    "Pago rechazado por el proveedor de pagos",
                    Severity.MEDIUM, paymentDeclinedPayload(brand));
            case ERR_ITEM_OUT_OF_STOCK -> buildErrorEvent(DOMAIN, ERR_ITEM_OUT_OF_STOCK,
                    "Articulo de la carta no disponible en la tienda",
                    Severity.LOW, itemOutOfStockPayload(brand));
            case ERR_KITCHEN_DELAY -> buildErrorEvent(DOMAIN, ERR_KITCHEN_DELAY,
                    "Preparacion por encima del SLA de cocina",
                    Severity.MEDIUM, kitchenDelayPayload(brand));
            case ERR_WRONG_ORDER_DELIVERED -> buildErrorEvent(DOMAIN, ERR_WRONG_ORDER_DELIVERED,
                    "El cliente recibio un pedido que no era el suyo",
                    Severity.HIGH, wrongOrderPayload(brand));
            case ERR_DRIVE_THRU_TIMEOUT -> buildErrorEvent(DOMAIN, ERR_DRIVE_THRU_TIMEOUT,
                    "Vehiculo abandona el drive-thru por exceso de espera",
                    Severity.MEDIUM, driveThruTimeoutPayload(brand));
            case ERR_DELIVERY_FAILED -> buildErrorEvent(DOMAIN, ERR_DELIVERY_FAILED,
                    "Entrega a domicilio fallida",
                    Severity.HIGH, deliveryFailedPayload(brand));
            case ERR_INGREDIENT_OUT_OF_STOCK -> buildErrorEvent(DOMAIN, ERR_INGREDIENT_OUT_OF_STOCK,
                    "Ingrediente agotado: articulos bloqueados en la carta",
                    Severity.MEDIUM, ingredientOutOfStockPayload(brand));
            case ERR_COLD_CHAIN_BREACH -> buildErrorEvent(DOMAIN, ERR_COLD_CHAIN_BREACH,
                    "Temperatura de congelador por encima del umbral permitido",
                    Severity.CRITICAL, coldChainBreachPayload(brand));
            default -> throw new IllegalArgumentException("Tipo de error no soportado: " + errorType);
        };
    }

    // --- Eventos normales: pedidos y cocina ---------------------------------

    private Event orderPlaced() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = orderPayload(brand, RandomUtils.randomFrom(CHANNELS), "PLACED");
        return buildNormalEvent(DOMAIN, ORDER_PLACED, payload);
    }

    private Event orderPaid() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = orderPayload(brand, RandomUtils.randomFrom(CHANNELS), "PAID");
        payload.put("paidAt", now());
        return buildNormalEvent(DOMAIN, ORDER_PAID, payload);
    }

    private Event kitchenPrepStarted() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = orderPayload(brand, RandomUtils.randomFrom(CHANNELS), "IN_PREP");
        payload.put("prepTimeSeconds", RandomUtils.nextInt(60, PREP_SLA_SECONDS));
        payload.put("stationId", "ST-" + RandomUtils.nextInt(1, 6));
        return buildNormalEvent(DOMAIN, KITCHEN_PREP_STARTED, payload);
    }

    private Event orderReady() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = orderPayload(brand, RandomUtils.randomFrom(CHANNELS), "READY");
        payload.put("prepTimeSeconds", RandomUtils.nextInt(60, PREP_SLA_SECONDS));
        return buildNormalEvent(DOMAIN, ORDER_READY, payload);
    }

    private Event orderDelivered() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = orderPayload(brand, RandomUtils.randomFrom(CHANNELS), "DELIVERED");
        payload.put("prepTimeSeconds", RandomUtils.nextInt(60, PREP_SLA_SECONDS));
        payload.put("deliveredAt", now());
        return buildNormalEvent(DOMAIN, ORDER_DELIVERED, payload);
    }

    // --- Eventos normales: drive-thru y canales -----------------------------

    private Event driveThruArrival() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = storePayload(brand);
        payload.put("channel", "DRIVE_THRU");
        payload.put("laneId", "LANE-" + RandomUtils.nextInt(1, 3));
        payload.put("vehiclePlate", faker.vehicle().licensePlate());
        payload.put("queuePosition", RandomUtils.nextInt(1, 8));
        return buildNormalEvent(DOMAIN, DRIVE_THRU_ARRIVAL, payload);
    }

    private Event driveThruOrder() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = orderPayload(brand, "DRIVE_THRU", "PLACED");
        payload.put("laneId", "LANE-" + RandomUtils.nextInt(1, 3));
        payload.put("waitTimeSeconds", RandomUtils.nextInt(30, 420));
        return buildNormalEvent(DOMAIN, DRIVE_THRU_ORDER, payload);
    }

    private Event kioskOrder() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = orderPayload(brand, "KIOSK", "PLACED");
        payload.put("kioskId", "KSK-" + RandomUtils.nextInt(1, 8));
        return buildNormalEvent(DOMAIN, KIOSK_ORDER, payload);
    }

    private Event appOrder() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = orderPayload(brand, "MOBILE_APP", "PLACED");
        payload.put("appUserId", "USR-" + RandomUtils.nextInt(100000, 999999));
        payload.put("loyaltyPoints", RandomUtils.nextInt(0, 500));
        return buildNormalEvent(DOMAIN, APP_ORDER, payload);
    }

    private Event deliveryDispatched() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = orderPayload(brand, "DELIVERY", "READY");
        payload.put("deliveryPartner", RandomUtils.randomFrom(DELIVERY_PARTNERS));
        payload.put("courierId", "CUR-" + RandomUtils.nextInt(1000, 9999));
        payload.put("etaMinutes", RandomUtils.nextInt(8, 45));
        return buildNormalEvent(DOMAIN, DELIVERY_DISPATCHED, payload);
    }

    // --- Eventos normales: inventario ---------------------------------------

    private Event inventoryUpdated() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = inventoryPayload(brand);
        payload.put("movement", RandomUtils.randomFrom("CONSUMPTION", "ADJUSTMENT", "COUNT"));
        return buildNormalEvent(DOMAIN, INVENTORY_UPDATED, payload);
    }

    private Event stockReplenished() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = inventoryPayload(brand);
        payload.put("movement", "REPLENISHMENT");
        payload.put("receivedQuantity", round(RandomUtils.nextDouble(20.0, 200.0)));
        payload.put("supplierId", "SUP-" + RandomUtils.nextInt(100, 999));
        return buildNormalEvent(DOMAIN, STOCK_REPLENISHED, payload);
    }

    private Event wasteRecorded() {
        FastFoodBrand brand = randomBrand();
        Map<String, Object> payload = inventoryPayload(brand);
        payload.put("movement", "WASTE");
        payload.put("wasteQuantity", round(RandomUtils.nextDouble(0.5, 15.0)));
        payload.put("wasteReason", RandomUtils.randomFrom("EXPIRED", "DROPPED", "OVERPRODUCTION", "QUALITY_CHECK"));
        return buildNormalEvent(DOMAIN, WASTE_RECORDED, payload);
    }

    // --- Payloads de error ---------------------------------------------------

    private Map<String, Object> paymentDeclinedPayload(FastFoodBrand brand) {
        Map<String, Object> payload = orderPayload(brand, RandomUtils.randomFrom(CHANNELS), "CANCELLED");
        payload.put("paymentMethod", RandomUtils.randomFrom("CREDIT_CARD", "DEBIT_CARD", "GIFT_CARD"));
        payload.put("declineCode", RandomUtils.randomFrom("INSUFFICIENT_FUNDS", "CARD_EXPIRED", "DO_NOT_HONOR", "TIMEOUT"));
        return payload;
    }

    private Map<String, Object> itemOutOfStockPayload(FastFoodBrand brand) {
        MenuItem unavailable = RandomUtils.randomFrom(brand.menu());
        Map<String, Object> payload = orderPayload(brand, RandomUtils.randomFrom(CHANNELS), "CANCELLED");
        payload.put("unavailableSku", unavailable.sku());
        payload.put("unavailableItem", unavailable.name());
        return payload;
    }

    private Map<String, Object> kitchenDelayPayload(FastFoodBrand brand) {
        Map<String, Object> payload = orderPayload(brand, RandomUtils.randomFrom(CHANNELS), "IN_PREP");
        payload.put("prepTimeSeconds", RandomUtils.nextInt(PREP_SLA_SECONDS + 1, 1800));
        payload.put("slaSeconds", PREP_SLA_SECONDS);
        payload.put("stationId", "ST-" + RandomUtils.nextInt(1, 6));
        return payload;
    }

    private Map<String, Object> wrongOrderPayload(FastFoodBrand brand) {
        Map<String, Object> payload = orderPayload(brand, RandomUtils.randomFrom(CHANNELS), "DELIVERED");
        payload.put("deliveredOrderId", orderId(brand));
        payload.put("complaintOpened", true);
        return payload;
    }

    private Map<String, Object> driveThruTimeoutPayload(FastFoodBrand brand) {
        Map<String, Object> payload = orderPayload(brand, "DRIVE_THRU", "CANCELLED");
        payload.put("laneId", "LANE-" + RandomUtils.nextInt(1, 3));
        payload.put("waitTimeSeconds", RandomUtils.nextInt(901, 2400));
        payload.put("timeoutThresholdSeconds", 900);
        return payload;
    }

    private Map<String, Object> deliveryFailedPayload(FastFoodBrand brand) {
        Map<String, Object> payload = orderPayload(brand, "DELIVERY", "CANCELLED");
        payload.put("deliveryPartner", RandomUtils.randomFrom(DELIVERY_PARTNERS));
        payload.put("courierId", "CUR-" + RandomUtils.nextInt(1000, 9999));
        payload.put("failureReason", RandomUtils.randomFrom(
                "ADDRESS_NOT_FOUND", "CUSTOMER_UNREACHABLE", "COURIER_ACCIDENT", "ORDER_DAMAGED"));
        return payload;
    }

    private Map<String, Object> ingredientOutOfStockPayload(FastFoodBrand brand) {
        Map<String, Object> payload = inventoryPayload(brand);
        payload.put("quantityOnHand", 0.0);
        payload.put("movement", "STOCKOUT");
        payload.put("blockedItems", brand.menu().stream()
                .filter(item -> !"DRINK".equals(item.category()))
                .limit(3)
                .map(MenuItem::name)
                .toList());
        return payload;
    }

    private Map<String, Object> coldChainBreachPayload(FastFoodBrand brand) {
        Map<String, Object> payload = inventoryPayload(brand);
        payload.put("freezerId", "FRZ-" + RandomUtils.nextInt(1, 5));
        payload.put("temperatureCelsius", round(RandomUtils.nextDouble(-6.0, 12.0)));
        payload.put("thresholdCelsius", COLD_CHAIN_THRESHOLD_CELSIUS);
        payload.put("breachMinutes", RandomUtils.nextInt(15, 240));
        return payload;
    }

    // --- Helpers -------------------------------------------------------------

    private FastFoodBrand randomBrand() {
        return RandomUtils.randomFrom(brands);
    }

    private Map<String, Object> storePayload(FastFoodBrand brand) {
        Market market = RandomUtils.randomFrom(MARKETS);
        return payload(
                "brand", brand.name(),
                "brandName", brand.displayName(),
                "storeId", brand.code() + "-" + RandomUtils.nextInt(1000, 9999),
                "storeCity", faker.address().city(),
                "country", market.country(),
                "currency", market.currency()
        );
    }

    private Map<String, Object> orderPayload(FastFoodBrand brand, String channel, String status) {
        Map<String, Object> payload = storePayload(brand);
        List<Map<String, Object>> items = items(brand);
        payload.put("orderId", orderId(brand));
        payload.put("channel", channel);
        payload.put("items", items);
        payload.put("itemCount", items.size());
        payload.put("totalAmount", totalAmount(items));
        payload.put("paymentMethod", RandomUtils.randomFrom(PAYMENT_METHODS));
        payload.put("orderStatus", status);
        payload.put("employeeId", "EMP-" + RandomUtils.nextInt(10000, 99999));
        return payload;
    }

    private Map<String, Object> inventoryPayload(FastFoodBrand brand) {
        Ingredient ingredient = RandomUtils.randomFrom(brand.ingredients());
        Map<String, Object> payload = storePayload(brand);
        payload.put("ingredientSku", ingredient.sku());
        payload.put("ingredientName", ingredient.name());
        payload.put("unit", ingredient.unit());
        payload.put("quantityOnHand", round(RandomUtils.nextDouble(0.0, 400.0)));
        payload.put("reorderLevel", round(RandomUtils.nextDouble(10.0, 60.0)));
        return payload;
    }

    private String orderId(FastFoodBrand brand) {
        return "ORD-" + brand.code() + "-" + RandomUtils.nextInt(10000000, 99999999);
    }

    private List<Map<String, Object>> items(FastFoodBrand brand) {
        List<Map<String, Object>> items = new ArrayList<>();
        int count = RandomUtils.nextInt(1, 5);
        for (int i = 0; i < count; i++) {
            MenuItem menuItem = RandomUtils.randomFrom(brand.menu());
            int quantity = RandomUtils.nextInt(1, 3);
            items.add(Map.of(
                    "sku", menuItem.sku(),
                    "name", menuItem.name(),
                    "category", menuItem.category(),
                    "quantity", quantity,
                    "unitPrice", menuItem.basePrice(),
                    "lineTotal", round(menuItem.basePrice() * quantity)
            ));
        }
        return items;
    }

    private double totalAmount(List<Map<String, Object>> items) {
        double total = items.stream()
                .mapToDouble(item -> ((Number) item.get("lineTotal")).doubleValue())
                .sum();
        return round(total);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private FieldDefinition fd(String name, String type, boolean required, String fakerExpression, String description) {
        return new FieldDefinition(name, type, required, fakerExpression, null, null, description);
    }

    private FieldDefinition fd(String name, String type, boolean required, String fakerExpression,
                               List<String> enumValues, String description) {
        return new FieldDefinition(name, type, required, fakerExpression, null, enumValues, description);
    }
}
