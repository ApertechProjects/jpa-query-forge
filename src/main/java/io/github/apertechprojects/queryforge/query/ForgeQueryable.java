package io.github.apertechprojects.queryforge.query;

import io.github.apertechprojects.queryforge.expression.ForgePredicate;
import io.github.apertechprojects.queryforge.expression.ForgeProperty;
import io.github.apertechprojects.queryforge.expression.ForgeSelector;
import io.github.apertechprojects.queryforge.internal.ClassProjection;
import io.github.apertechprojects.queryforge.internal.QueryRunner;
import io.github.apertechprojects.queryforge.internal.QueryRunner.Aggregate;
import io.github.apertechprojects.queryforge.internal.QuerySpec;
import io.github.apertechprojects.queryforge.internal.QuerySpec.SortKey;
import io.github.apertechprojects.queryforge.internal.expression.LambdaTranslator;
import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Query over an entity, in the style of EF Core's {@code IQueryable}:
 * <pre>{@code
 * Page<OrderRow> page = purchaseOrderRepository
 *         .where(o -> o.status == OrderStatus.APPROVED && o.amount.compareTo(limit) > 0)
 *         .orderBy(o -> o.supplier.name).thenByDescending(PurchaseOrder::getCreatedAt)
 *         .select(OrderRow.class)
 *         .toPage(pageable);
 * }</pre>
 * Instances are immutable: every method returns a new query, so a base query can be extended conditionally.
 * Lambdas are translated when they are passed in; captured variables are read at that moment.
 * <p>
 * Sort keys are lambdas over the query model ({@code o -> o.supplier.name}) or entity getters
 * ({@code PurchaseOrder::getCreatedAt}). Paged queries without a sort key are ordered by id.
 *
 * @param <T> entity
 * @param <Q> generated query model of the entity
 */
public class ForgeQueryable<T, Q> {

    protected final JpaQueryForgeRepository<T, ?, Q> repository;
    protected final QuerySpec spec;

    ForgeQueryable(JpaQueryForgeRepository<T, ?, Q> repository, QuerySpec spec) {
        this.repository = repository;
        this.spec = spec;
    }

    public static <T, Q> ForgeQueryable<T, Q> of(JpaQueryForgeRepository<T, ?, Q> repository) {
        return new ForgeQueryable<>(repository, QuerySpec.ALL);
    }

    /** Adds a condition; all conditions must hold. */
    public ForgeQueryable<T, Q> where(ForgePredicate<Q> predicate) {
        return new ForgeQueryable<>(repository, spec.withWhere(LambdaTranslator.translate(predicate)));
    }

    /** Sorts ascending by a model attribute, replacing any previous order: {@code o -> o.supplier.name}. */
    public OrderedForgeQueryable<T, Q> orderBy(ForgeSelector<Q, ?> selector) {
        return ordered(List.of(sortKey(selector, false)));
    }

    /** Sorts ascending by an entity getter, replacing any previous order: {@code PurchaseOrder::getCreatedAt}. */
    public OrderedForgeQueryable<T, Q> orderBy(ForgeProperty<? super T, ?> property) {
        return ordered(List.of(sortKey(property, false)));
    }

    public OrderedForgeQueryable<T, Q> orderByDescending(ForgeSelector<Q, ?> selector) {
        return ordered(List.of(sortKey(selector, true)));
    }

    public OrderedForgeQueryable<T, Q> orderByDescending(ForgeProperty<? super T, ?> property) {
        return ordered(List.of(sortKey(property, true)));
    }

    /** {@code SELECT DISTINCT}. */
    public ForgeQueryable<T, Q> distinct() {
        return new ForgeQueryable<>(repository, spec.withDistinct());
    }

    /** Returns page {@code pageNumber} (0-based) of {@code pageSize} rows from {@link #toList}. */
    public ForgeQueryable<T, Q> page(int pageNumber, int pageSize) {
        checkPage(pageNumber, pageSize);
        return new ForgeQueryable<>(repository, spec.withRange(pageNumber * pageSize, pageSize));
    }

    /** Limits {@link #toList} to the first {@code count} rows. */
    public ForgeQueryable<T, Q> take(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1");
        }
        return new ForgeQueryable<>(repository, spec.withRange(spec.offset(), count));
    }

    /**
     * Projects each row onto a {@link io.github.apertechprojects.queryforge.annotation.ForgeModel} class: fields
     * are selected from the attributes of the same name or from their {@code @ForgeField} paths
     * ({@code @ForgeField("supplier.name") String supplierName}). Missing attributes and type mismatches are
     * reported before the query runs, and at compile time when {@code @ForgeModel(entity = ...)} is set.
     */
    public <R> ForgeProjection<R> select(Class<R> modelClass) {
        return new ForgeProjection<>(QueryRunner.of(repository), spec, ClassProjection.of(modelClass));
    }

    /**
     * Projects each row with a lambda over the query model, e.g. a single value: {@code select(o -> o.supplier.name)}.
     * Only the fields the lambda reads are selected; the lambda then runs once per row. Prefer
     * {@link #select(Class)} for DTOs.
     */
    public <R> ForgeProjection<R> select(ForgeSelector<Q, R> selector) {
        return new ForgeProjection<>(QueryRunner.of(repository), spec, LambdaTranslator.projection(selector));
    }

    public List<T> toList() {
        return runner().list(spec);
    }

    /**
     * Page with the total count. Only page number and size of {@code pageable} are used; its sort is not applied
     * (use {@code orderBy}). An unpaged {@code pageable} returns all rows as one page.
     */
    public Page<T> toPage(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return new PageImpl<>(runner().list(spec.withRange(null, null)));
        }
        return toPage(pageable.getPageNumber(), pageable.getPageSize());
    }

    public Page<T> toPage(int pageNumber, int pageSize) {
        checkPage(pageNumber, pageSize);
        return runner().page(spec, pageNumber, pageSize);
    }

    /** Number of matching rows; order and paging are ignored. */
    public long count() {
        return runner().count(spec);
    }

    public long count(ForgePredicate<Q> predicate) {
        return where(predicate).count();
    }

    /** Whether any row matches; runs {@code SELECT 1} limited to one row. */
    public boolean any() {
        return runner().exists(spec);
    }

    public boolean any(ForgePredicate<Q> predicate) {
        return where(predicate).any();
    }

    /** First row in the query order, if any. Without an order the row is chosen by the database. */
    public Optional<T> firstOrDefault() {
        return runner().list(spec.first()).stream().findFirst();
    }

    public Optional<T> firstOrDefault(ForgePredicate<Q> predicate) {
        return where(predicate).firstOrDefault();
    }

    /** Sum over the matching rows, zero when there are none: {@code sum(o -> o.amount)}. */
    @SuppressWarnings("unchecked")
    public <R extends Number> R sum(ForgeSelector<Q, R> selector) {
        return (R) runner().aggregate(spec, Aggregate.SUM, LambdaTranslator.translateSelector(selector));
    }

    @SuppressWarnings("unchecked")
    public <R extends Comparable<? super R>> Optional<R> min(ForgeSelector<Q, R> selector) {
        return Optional.ofNullable((R) runner().aggregate(spec, Aggregate.MIN, LambdaTranslator.translateSelector(selector)));
    }

    @SuppressWarnings("unchecked")
    public <R extends Comparable<? super R>> Optional<R> max(ForgeSelector<Q, R> selector) {
        return Optional.ofNullable((R) runner().aggregate(spec, Aggregate.MAX, LambdaTranslator.translateSelector(selector)));
    }

    /** Average over the matching rows, empty when there are none. */
    public Optional<Double> average(ForgeSelector<Q, ? extends Number> selector) {
        return Optional.ofNullable((Double) runner().aggregate(spec, Aggregate.AVERAGE, LambdaTranslator.translateSelector(selector)));
    }

    OrderedForgeQueryable<T, Q> ordered(List<SortKey> sortKeys) {
        return new OrderedForgeQueryable<>(repository, spec.withSortKeys(sortKeys));
    }

    static SortKey sortKey(Serializable selector, boolean descending) {
        return new SortKey(LambdaTranslator.translateSelector(selector), descending);
    }

    static void checkPage(int pageNumber, int pageSize) {
        if (pageNumber < 0 || pageSize < 1) {
            throw new IllegalArgumentException("pageNumber must be >= 0 and pageSize >= 1");
        }
    }

    private QueryRunner<T> runner() {
        return QueryRunner.of(repository);
    }
}
