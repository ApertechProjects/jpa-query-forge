package io.github.apertechprojects.queryforge.internal;

import io.github.apertechprojects.queryforge.internal.expression.Formula;
import io.github.apertechprojects.queryforge.internal.expression.Operand;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a query built with {@link io.github.apertechprojects.queryforge.query.ForgeQueryable} needs to run.
 *
 * @param where                   conditions, all must hold
 * @param sortKeys                ORDER BY, in order
 * @param distinct                SELECT DISTINCT
 * @param offset                  first row, or {@code null}
 * @param limit                   maximum number of rows, or {@code null}
 * @param orderByIdWhenUnordered  order by id when there are no sort keys (paged queries, so pages do not overlap)
 */
public record QuerySpec(List<Formula> where, List<SortKey> sortKeys, boolean distinct, Integer offset, Integer limit,
                        boolean orderByIdWhenUnordered) {

    public static final QuerySpec ALL = new QuerySpec(List.of(), List.of(), false, null, null, false);

    public record SortKey(Operand operand, boolean descending) {
    }

    public QuerySpec withWhere(Formula formula) {
        return new QuerySpec(append(where, formula), sortKeys, distinct, offset, limit, orderByIdWhenUnordered);
    }

    public QuerySpec withSortKeys(List<SortKey> sortKeys) {
        return new QuerySpec(where, List.copyOf(sortKeys), distinct, offset, limit, orderByIdWhenUnordered);
    }

    public QuerySpec withSortKey(SortKey sortKey) {
        return withSortKeys(append(sortKeys, sortKey));
    }

    public QuerySpec withDistinct() {
        return new QuerySpec(where, sortKeys, true, offset, limit, orderByIdWhenUnordered);
    }

    /** Rows {@code offset .. offset + limit}; a paged query without sort keys is ordered by id. */
    public QuerySpec withRange(Integer offset, Integer limit) {
        return new QuerySpec(where, sortKeys, distinct, offset, limit, limit != null);
    }

    /** Same rows, first one only, without the id ordering of paged queries. */
    public QuerySpec first() {
        return new QuerySpec(where, sortKeys, distinct, offset, 1, false);
    }

    private static <E> List<E> append(List<E> list, E element) {
        List<E> result = new ArrayList<>(list);
        result.add(element);
        return List.copyOf(result);
    }
}
