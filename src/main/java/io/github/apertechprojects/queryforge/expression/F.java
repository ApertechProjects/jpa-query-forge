package io.github.apertechprojects.queryforge.expression;

/**
 * SQL functions that have no Java operator, for use inside {@link ForgePredicate} lambdas:
 * <pre>{@code
 * purchaseOrderRepository.where(o -> F.between(o.createdAt, from, to) && F.like(o.description, "%cement%"))
 * }</pre>
 * The methods are markers recognized by the translator; calling them directly throws.
 */
public final class F {

    private F() {
    }

    /** SQL {@code LIKE} with a raw pattern ({@code %} and {@code _} are wildcards). */
    public static boolean like(String value, String pattern) {
        throw notInLambda();
    }

    /** SQL {@code NOT LIKE} with a raw pattern. */
    public static boolean notLike(String value, String pattern) {
        throw notInLambda();
    }

    /** SQL {@code BETWEEN}: {@code from <= value <= to}. */
    public static <T extends Comparable<? super T>> boolean between(T value, T from, T to) {
        throw notInLambda();
    }

    private static UnsupportedOperationException notInLambda() {
        return new UnsupportedOperationException("F functions can only be used inside where lambdas");
    }
}
