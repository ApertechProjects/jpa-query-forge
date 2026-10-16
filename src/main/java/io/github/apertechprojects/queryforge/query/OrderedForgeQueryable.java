package io.github.apertechprojects.queryforge.query;

import io.github.apertechprojects.queryforge.expression.ForgeProperty;
import io.github.apertechprojects.queryforge.expression.ForgeSelector;
import io.github.apertechprojects.queryforge.internal.QuerySpec;
import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;

/**
 * {@link ForgeQueryable} right after {@code orderBy}, like EF Core's {@code IOrderedQueryable}: only here can
 * further sort keys be added with {@code thenBy}.
 */
public final class OrderedForgeQueryable<T, Q> extends ForgeQueryable<T, Q> {

    OrderedForgeQueryable(JpaQueryForgeRepository<T, ?, Q> repository, QuerySpec spec) {
        super(repository, spec);
    }

    public OrderedForgeQueryable<T, Q> thenBy(ForgeSelector<Q, ?> selector) {
        return then(sortKey(selector, false));
    }

    public OrderedForgeQueryable<T, Q> thenBy(ForgeProperty<? super T, ?> property) {
        return then(sortKey(property, false));
    }

    public OrderedForgeQueryable<T, Q> thenByDescending(ForgeSelector<Q, ?> selector) {
        return then(sortKey(selector, true));
    }

    public OrderedForgeQueryable<T, Q> thenByDescending(ForgeProperty<? super T, ?> property) {
        return then(sortKey(property, true));
    }

    private OrderedForgeQueryable<T, Q> then(QuerySpec.SortKey sortKey) {
        return new OrderedForgeQueryable<>(repository, spec.withSortKey(sortKey));
    }
}
