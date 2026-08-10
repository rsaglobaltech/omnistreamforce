package de.omnistreamforce.domain.fastfood;

import java.util.List;
import java.util.Optional;

/**
 * Franquicias de comida rapida soportadas por el dominio fastfood.
 * Cada marca aporta su propia carta y los ingredientes que se controlan
 * en el inventario de sus tiendas.
 *
 * <p>Para anadir una franquicia nueva basta con anadir una constante aqui:
 * el generador la incluira automaticamente en la rotacion de eventos.</p>
 */
public enum FastFoodBrand {

    BURGER_KING("Burger King", "BK",
            List.of(
                    new MenuItem("BK-WHP", "Whopper", 6.49, "BURGER"),
                    new MenuItem("BK-WJR", "Whopper Jr.", 3.99, "BURGER"),
                    new MenuItem("BK-BKG", "Bacon King", 7.99, "BURGER"),
                    new MenuItem("BK-CRY", "Chicken Royale", 5.79, "CHICKEN"),
                    new MenuItem("BK-NUG", "Chicken Nuggets 9", 4.29, "CHICKEN"),
                    new MenuItem("BK-FRY", "King Fries", 2.79, "SIDE"),
                    new MenuItem("BK-ONR", "Onion Rings", 2.99, "SIDE"),
                    new MenuItem("BK-COK", "Coca-Cola 0.5L", 2.19, "DRINK"),
                    new MenuItem("BK-SUN", "Sundae Chocolate", 1.99, "DESSERT")
            ),
            List.of(
                    new Ingredient("BK-ING-BEEF", "Flame-grilled beef patty", "UNIT"),
                    new Ingredient("BK-ING-BUN", "Sesame bun", "UNIT"),
                    new Ingredient("BK-ING-CHK", "Chicken fillet", "UNIT"),
                    new Ingredient("BK-ING-FRZ", "Frozen fries", "KG"),
                    new Ingredient("BK-ING-LET", "Lettuce", "KG"),
                    new Ingredient("BK-ING-CHS", "Cheese slice", "UNIT"),
                    new Ingredient("BK-ING-SYR", "Soda syrup", "L"),
                    new Ingredient("BK-ING-ICE", "Ice cream mix", "L")
            )),

    MCDONALDS("McDonald's", "MCD",
            List.of(
                    new MenuItem("MCD-BGM", "Big Mac", 6.19, "BURGER"),
                    new MenuItem("MCD-QPC", "Quarter Pounder with Cheese", 6.59, "BURGER"),
                    new MenuItem("MCD-CBG", "Cheeseburger", 2.49, "BURGER"),
                    new MenuItem("MCD-MCC", "McChicken", 5.29, "CHICKEN"),
                    new MenuItem("MCD-NUG", "Chicken McNuggets 9", 4.49, "CHICKEN"),
                    new MenuItem("MCD-FRY", "French Fries Large", 2.89, "SIDE"),
                    new MenuItem("MCD-COK", "Coca-Cola 0.5L", 2.09, "DRINK"),
                    new MenuItem("MCD-MCF", "McFlurry Oreo", 3.29, "DESSERT"),
                    new MenuItem("MCD-HPM", "Happy Meal", 4.99, "MENU")
            ),
            List.of(
                    new Ingredient("MCD-ING-BEEF", "Beef patty 10:1", "UNIT"),
                    new Ingredient("MCD-ING-BUN", "Regular bun", "UNIT"),
                    new Ingredient("MCD-ING-CHK", "Chicken patty", "UNIT"),
                    new Ingredient("MCD-ING-FRZ", "Frozen fries", "KG"),
                    new Ingredient("MCD-ING-PKL", "Pickles", "KG"),
                    new Ingredient("MCD-ING-CHS", "Cheese slice", "UNIT"),
                    new Ingredient("MCD-ING-SYR", "Soda syrup", "L"),
                    new Ingredient("MCD-ING-ICE", "Ice cream mix", "L")
            ));

    /** Articulo de la carta de una franquicia. */
    public record MenuItem(String sku, String name, double basePrice, String category) {
    }

    /** Ingrediente controlado en el inventario de la tienda. */
    public record Ingredient(String sku, String name, String unit) {
    }

    private final String displayName;
    private final String code;
    private final List<MenuItem> menu;
    private final List<Ingredient> ingredients;

    FastFoodBrand(String displayName, String code, List<MenuItem> menu, List<Ingredient> ingredients) {
        this.displayName = displayName;
        this.code = code;
        this.menu = menu;
        this.ingredients = ingredients;
    }

    public String displayName() {
        return displayName;
    }

    /** Codigo corto de la marca, usado como prefijo de ids (BK, MCD, ...). */
    public String code() {
        return code;
    }

    public List<MenuItem> menu() {
        return menu;
    }

    public List<Ingredient> ingredients() {
        return ingredients;
    }

    /** Nombres de todas las marcas, para exponerlos como enum en el EventSchema. */
    public static List<String> names() {
        return List.of(values()).stream().map(Enum::name).toList();
    }

    /** Busca una marca por su nombre de constante o por su codigo corto (case-insensitive). */
    public static Optional<FastFoodBrand> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return List.of(values()).stream()
                .filter(b -> b.name().equalsIgnoreCase(value) || b.code.equalsIgnoreCase(value))
                .findFirst();
    }
}
