package io.github.apertechprojects.queryforge.expression;

import java.io.Serializable;

/**
 * Entity getter used as a sort key: {@code orderByDescending(PurchaseOrder::getCreatedAt)}.
 * <p>
 * The getter is never called; the attribute is read from its bytecode (or derived from the getter name).
 * For navigations use a {@link ForgeSelector} over the query model: {@code orderBy(o -> o.supplier.name)}.
 *
 * @param <T> entity
 * @param <R> attribute type
 */
@FunctionalInterface
public interface ForgeProperty<T, R> extends Serializable {

    R get(T entity);
}
