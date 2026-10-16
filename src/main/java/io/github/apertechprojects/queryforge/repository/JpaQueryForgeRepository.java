package io.github.apertechprojects.queryforge.repository;

import io.github.apertechprojects.queryforge.expression.ForgePredicate;
import io.github.apertechprojects.queryforge.expression.ForgeProperty;
import io.github.apertechprojects.queryforge.expression.ForgeSelector;
import io.github.apertechprojects.queryforge.query.ForgeProjection;
import io.github.apertechprojects.queryforge.query.ForgeQueryable;
import io.github.apertechprojects.queryforge.query.OrderedForgeQueryable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

/**
 * Spring Data JPA repository with EF Core style queries:
 * <pre>{@code
 * purchaseOrderRepository.where(o -> o.status == OrderStatus.APPROVED && o.supplier.name.startsWith("Azer"))
 *         .orderByDescending(PurchaseOrder::getCreatedAt)
 *         .select(OrderRow.class)
 *         .toList();
 * }</pre>
 * All operations are default methods, so no custom repository base class or factory bean is required.
 *
 * @param <T>  entity
 * @param <ID> id type
 * @param <Q>  query model generated for the entity ({@code QPurchaseOrder} for {@code PurchaseOrder})
 */
@NoRepositoryBean
public interface JpaQueryForgeRepository<T, ID, Q> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    /** Query over all rows, e.g. for aggregates: {@code repository.query().sum(o -> o.amount)}. */
    default ForgeQueryable<T, Q> query() {
        return ForgeQueryable.of(this);
    }

    /**
     * Query with a where lambda over the query model, e.g.
     * {@code o -> o.amount.compareTo(limit) > 0 && o.supplier.name.startsWith("Azer")}.
     *
     * @see ForgePredicate for what a lambda may contain
     */
    default ForgeQueryable<T, Q> where(ForgePredicate<Q> predicate) {
        return query().where(predicate);
    }

    /** Query sorted by a model attribute: {@code orderBy(o -> o.supplier.name)}. */
    default OrderedForgeQueryable<T, Q> orderBy(ForgeSelector<Q, ?> selector) {
        return query().orderBy(selector);
    }

    /** Query sorted by an entity getter: {@code orderBy(PurchaseOrder::getCreatedAt)}. */
    default OrderedForgeQueryable<T, Q> orderBy(ForgeProperty<? super T, ?> property) {
        return query().orderBy(property);
    }

    default OrderedForgeQueryable<T, Q> orderByDescending(ForgeSelector<Q, ?> selector) {
        return query().orderByDescending(selector);
    }

    default OrderedForgeQueryable<T, Q> orderByDescending(ForgeProperty<? super T, ?> property) {
        return query().orderByDescending(property);
    }

    /** All rows projected onto a {@code @ForgeModel} class: {@code select(OrderRow.class)}. */
    default <R> ForgeProjection<R> select(Class<R> modelClass) {
        return query().select(modelClass);
    }

    /** Projection of all rows with a lambda: {@code select(o -> o.name)}. */
    default <R> ForgeProjection<R> select(ForgeSelector<Q, R> selector) {
        return query().select(selector);
    }

    default long count(ForgePredicate<Q> predicate) {
        return query().count(predicate);
    }

    default boolean any(ForgePredicate<Q> predicate) {
        return query().any(predicate);
    }

    default Optional<T> firstOrDefault(ForgePredicate<Q> predicate) {
        return query().firstOrDefault(predicate);
    }
}
