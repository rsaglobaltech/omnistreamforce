package de.omnistreamforce.util;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utilidades para generacion de datos aleatorios reutilizables.
 */
public final class RandomUtils {

    private RandomUtils() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static int nextInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public static long nextLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    public static double nextDouble(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    public static boolean nextBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    public static boolean chance(double probability) {
        if (probability <= 0.0) return false;
        if (probability >= 1.0) return true;
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    public static <T> T randomFrom(List<T> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("La lista de items no puede estar vacia");
        }
        return items.get(ThreadLocalRandom.current().nextInt(items.size()));
    }

    @SafeVarargs
    public static <T> T randomFrom(T... items) {
        if (items == null || items.length == 0) {
            throw new IllegalArgumentException("Los items no pueden estar vacios");
        }
        return items[ThreadLocalRandom.current().nextInt(items.length)];
    }
}