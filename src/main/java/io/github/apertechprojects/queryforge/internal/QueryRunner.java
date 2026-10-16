package io.github.apertechprojects.queryforge.internal;

import io.github.apertechprojects.queryforge.context.QueryForgeContext;
import io.github.apertechprojects.queryforge.exception.QueryForgeMappingException;
import io.github.apertechprojects.queryforge.internal.QuerySpec.SortKey;
import io.github.apertechprojects.queryforge.internal.expression.Operand;
import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.metamodel.EntityType;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs {@link QuerySpec}s with the JPA Criteria API.
 *
 * @param <T> entity
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class QueryRunner<T> {

    public enum Aggregate {
        SUM, MIN, MAX, AVERAGE
    }

    private static final Map<Class<?>, Class<?>> ENTITY_CLASSES = new ConcurrentHashMap<>();

    private final EntityManager entityManager;
    private final Class<T> entityClass;

    private QueryRunner(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    public static <T> QueryRunner<T> of(JpaQueryForgeRepository<T, ?, ?> repository) {
        Class<?> entityClass = ENTITY_CLASSES.computeIfAbsent(repository.getClass(), type -> {
            Class<?>[] arguments = GenericTypeResolver.resolveTypeArguments(type, JpaQueryForgeRepository.class);
            if (arguments == null || arguments[0] == null) {
                throw new IllegalStateException("Entity class could not be resolved for repository " + type.getName());
            }
            return arguments[0];
        });
        return new QueryRunner<>(QueryForgeContext.entityManager(), (Class<T>) entityClass);
    }

    public List<T> list(QuerySpec spec) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(entityClass);
        Root<T> root = query.from(entityClass);
        ExpressionPredicateBuilder expressions = new ExpressionPredicateBuilder(root, builder);
        query.select(root).distinct(spec.distinct());
        where(query, spec, expressions, builder);
        query.orderBy(orders(spec, root, builder, expressions));
        return range(entityManager.createQuery(query), spec).getResultList();
    }

    public Page<T> page(QuerySpec spec, int pageNumber, int pageSize) {
        List<T> content = list(spec.withRange(pageNumber * pageSize, pageSize));
        return PageableExecutionUtils.getPage(content, PageRequest.of(pageNumber, pageSize, sort(spec)), () -> count(spec));
    }

    public <R> List<R> project(QuerySpec spec, Projection<R> projection) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = builder.createTupleQuery();
        Root<T> root = query.from(entityClass);
        ExpressionPredicateBuilder expressions = new ExpressionPredicateBuilder(root, builder);

        List<Selection<?>> selections = new ArrayList<>();
        List<Class<?>> selectedTypes = new ArrayList<>();
        for (int i = 0; i < projection.getPaths().size(); i++) {
            Expression<?> path;
            try {
                path = expressions.path(projection.getPaths().get(i));
            } catch (IllegalArgumentException e) {
                throw new QueryForgeMappingException(projection.describe(i) + ": no such attribute in " + entityClass.getName(), e);
            }
            selections.add(path);
            selectedTypes.add(path.getJavaType());
        }
        projection.check(selectedTypes);
        List<Order> orders = orders(spec, root, builder, expressions);
        if (spec.distinct()) {
            // SELECT DISTINCT requires the ORDER BY expressions in the select list (SQL Server, PostgreSQL)
            for (SortKey sortKey : spec.sortKeys()) {
                if (!(sortKey.operand() instanceof Operand.FieldRef field && projection.getPaths().contains(field.path()))) {
                    selections.add(expressions.expression(sortKey.operand()));
                }
            }
        }
        query.multiselect(selections).distinct(spec.distinct());
        where(query, spec, expressions, builder);
        query.orderBy(orders);

        int width = projection.getPaths().size();
        List<R> result = new ArrayList<>();
        for (Tuple tuple : range(entityManager.createQuery(query), spec).getResultList()) {
            Object[] values = new Object[width];
            for (int i = 0; i < width; i++) {
                values[i] = tuple.get(i);
            }
            result.add(projection.map(values));
        }
        return result;
    }

    public <R> Page<R> projectPage(QuerySpec spec, Projection<R> projection, int pageNumber, int pageSize) {
        List<R> content = project(spec.withRange(pageNumber * pageSize, pageSize), projection);
        return PageableExecutionUtils.getPage(content, PageRequest.of(pageNumber, pageSize, sort(spec)), () -> count(spec));
    }

    /** Number of matching entities; order and range are ignored. */
    public long count(QuerySpec spec) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<T> root = query.from(entityClass);
        query.select(spec.distinct() ? builder.countDistinct(root) : builder.count(root));
        where(query, spec, new ExpressionPredicateBuilder(root, builder), builder);
        return entityManager.createQuery(query).getSingleResult();
    }

    /** {@code SELECT 1 ... } limited to one row: no entity is loaded. */
    public boolean exists(QuerySpec spec) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Integer> query = builder.createQuery(Integer.class);
        Root<T> root = query.from(entityClass);
        query.select(builder.literal(1));
        where(query, spec, new ExpressionPredicateBuilder(root, builder), builder);
        return !entityManager.createQuery(query).setMaxResults(1).getResultList().isEmpty();
    }

    /**
     * Aggregate of {@code operand} over the matching rows, converted to the attribute type. SUM of no rows is
     * zero; the others are {@code null}.
     */
    public Object aggregate(QuerySpec spec, Aggregate aggregate, Operand operand) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = builder.createQuery(Object.class);
        Root<T> root = query.from(entityClass);
        ExpressionPredicateBuilder expressions = new ExpressionPredicateBuilder(root, builder);
        Expression value = expressions.expression(operand);
        Expression<?> selection = switch (aggregate) {
            case SUM -> builder.sum(value);
            case MIN -> builder.least(value);
            case MAX -> builder.greatest(value);
            case AVERAGE -> builder.avg(value);
        };
        query.select(selection);
        where(query, spec, expressions, builder);
        Object result = entityManager.createQuery(query).getSingleResult();
        if (aggregate == Aggregate.AVERAGE) {
            return result == null ? null : ((Number) result).doubleValue();
        }
        if (result == null && aggregate == Aggregate.SUM) {
            result = 0;
        }
        return ExpressionPredicateBuilder.coerce(result, value.getJavaType());
    }

    private static void where(AbstractQuery<?> query, QuerySpec spec, ExpressionPredicateBuilder expressions, CriteriaBuilder builder) {
        if (!spec.where().isEmpty()) {
            query.where(builder.and(spec.where().stream().map(expressions::build).toArray(Predicate[]::new)));
        }
    }

    private List<Order> orders(QuerySpec spec, Root<T> root, CriteriaBuilder builder, ExpressionPredicateBuilder expressions) {
        List<Order> orders = new ArrayList<>();
        for (SortKey sortKey : spec.sortKeys()) {
            Expression<?> expression = expressions.expression(sortKey.operand());
            orders.add(sortKey.descending() ? builder.desc(expression) : builder.asc(expression));
        }
        EntityType<T> model = root.getModel();
        if (orders.isEmpty() && spec.orderByIdWhenUnordered() && model.hasSingleIdAttribute()) {
            orders.add(builder.asc(root.get(model.getId(model.getIdType().getJavaType()))));
        }
        return orders;
    }

    private static <X> TypedQuery<X> range(TypedQuery<X> query, QuerySpec spec) {
        if (spec.offset() != null) {
            query.setFirstResult(spec.offset());
        }
        if (spec.limit() != null) {
            query.setMaxResults(spec.limit());
        }
        return query;
    }

    /** Sort of the returned page; only plain attribute keys can be expressed as a Spring {@link Sort}. */
    private static Sort sort(QuerySpec spec) {
        List<Sort.Order> orders = new ArrayList<>();
        for (SortKey sortKey : spec.sortKeys()) {
            if (sortKey.operand() instanceof Operand.FieldRef field) {
                orders.add(new Sort.Order(sortKey.descending() ? Sort.Direction.DESC : Sort.Direction.ASC, field.path()));
            }
        }
        return Sort.by(orders);
    }
}
