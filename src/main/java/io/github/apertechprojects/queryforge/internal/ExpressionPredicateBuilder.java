package io.github.apertechprojects.queryforge.internal;

import io.github.apertechprojects.queryforge.exception.QueryForgeUnsupportedExpressionException;
import io.github.apertechprojects.queryforge.internal.expression.Formula;
import io.github.apertechprojects.queryforge.internal.expression.Operand;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.util.ClassUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JPA predicate of a translated where lambda. Navigations ({@code o.supplier.name}) are joined like
 * EF Core does: a required association ({@code optional = false}) is an INNER join, an optional one a LEFT join,
 * and everything after an optional association stays LEFT so its missing rows are kept. Conditions on optional
 * associations therefore combine with {@code ||} as written. One builder serves a whole query, so conditions,
 * sort keys and selections share the joins.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class ExpressionPredicateBuilder {

    private final Root<?> root;
    private final CriteriaBuilder builder;
    /** Joins of this query, reused by conditions, sort keys and selections. Keyed by identity of the From. */
    private final Map<From<?, ?>, Map<String, Join<?, ?>>> joins = new IdentityHashMap<>();
    /** Joins reached through LEFT joins only; their children must stay LEFT. */
    private final Map<From<?, ?>, Boolean> optionalChain = new IdentityHashMap<>();

    ExpressionPredicateBuilder(Root<?> root, CriteriaBuilder builder) {
        this.root = root;
        this.builder = builder;
    }

    Predicate build(Formula formula) {
        if (formula instanceof Formula.Constant constant) {
            return constant.value() ? builder.conjunction() : builder.disjunction();
        }
        if (formula instanceof Formula.And and) {
            return builder.and(and.parts().stream().map(this::build).toArray(Predicate[]::new));
        }
        if (formula instanceof Formula.Or or) {
            return builder.or(or.parts().stream().map(this::build).toArray(Predicate[]::new));
        }
        if (formula instanceof Formula.Not not) {
            return negate(not.formula());
        }
        if (formula instanceof Formula.Comparison comparison) {
            return comparison(comparison.operator(), comparison.left(), comparison.right());
        }
        if (formula instanceof Formula.IsNull isNull) {
            return expression(isNull.operand()).isNull();
        }
        if (formula instanceof Formula.Like like) {
            Expression<String> value = expression(like.operand());
            return like.escape() == null ? builder.like(value, like.pattern()) : builder.like(value, like.pattern(), like.escape());
        }
        if (formula instanceof Formula.In in) {
            if (in.values().isEmpty()) {
                return builder.disjunction();
            }
            Expression<?> value = expression(in.operand());
            CriteriaBuilder.In<Object> predicate = builder.in((Expression<Object>) value);
            in.values().forEach(v -> predicate.value(coerce(v, value.getJavaType())));
            return predicate;
        }
        if (formula instanceof Formula.IsMember isMember) {
            return builder.isMember(isMember.value(), (Expression<Collection<Object>>) (Expression) expression(isMember.collection()));
        }
        if (formula instanceof Formula.Between between) {
            Expression<Comparable> value = expression(between.operand());
            return builder.between(value, comparable(between.from(), value), comparable(between.to(), value));
        }
        if (formula instanceof Formula.IsEmpty isEmpty) {
            return builder.isEmpty((Expression<Collection<?>>) (Expression) expression(isEmpty.collection()));
        }
        if (formula instanceof Formula.IsTrue isTrue) {
            return builder.isTrue(expression(isTrue.operand()));
        }
        throw new QueryForgeUnsupportedExpressionException("Unsupported condition: " + formula);
    }

    private Predicate negate(Formula formula) {
        if (formula instanceof Formula.IsNull isNull) {
            return expression(isNull.operand()).isNotNull();
        }
        if (formula instanceof Formula.Like like) {
            Expression<String> value = expression(like.operand());
            return like.escape() == null ? builder.notLike(value, like.pattern()) : builder.notLike(value, like.pattern(), like.escape());
        }
        if (formula instanceof Formula.IsEmpty isEmpty) {
            return builder.isNotEmpty((Expression<Collection<?>>) (Expression) expression(isEmpty.collection()));
        }
        if (formula instanceof Formula.IsTrue isTrue) {
            return builder.isFalse(expression(isTrue.operand()));
        }
        return builder.not(build(formula));
    }

    private Predicate comparison(Formula.Operator operator, Operand left, Operand right) {
        if (left instanceof Operand.Value && !(right instanceof Operand.Value)) {
            return comparison(operator.flip(), right, left);
        }
        Expression<Comparable> expression = expression(left);
        if (right instanceof Operand.Value value) {
            if (value.value() == null) {
                if (operator == Formula.Operator.EQ) {
                    return expression.isNull();
                }
                if (operator == Formula.Operator.NE) {
                    return expression.isNotNull();
                }
                throw new QueryForgeUnsupportedExpressionException("Values compared with " + operator + " must not be null");
            }
            Comparable coerced = (Comparable) coerce(value.value(), expression.getJavaType());
            return switch (operator) {
                case EQ -> builder.equal(expression, coerced);
                case NE -> builder.notEqual(expression, coerced);
                case GT -> builder.greaterThan(expression, coerced);
                case GE -> builder.greaterThanOrEqualTo(expression, coerced);
                case LT -> builder.lessThan(expression, coerced);
                case LE -> builder.lessThanOrEqualTo(expression, coerced);
            };
        }
        Expression<Comparable> other = expression(right);
        return switch (operator) {
            case EQ -> builder.equal(expression, other);
            case NE -> builder.notEqual(expression, other);
            case GT -> builder.greaterThan(expression, other);
            case GE -> builder.greaterThanOrEqualTo(expression, other);
            case LT -> builder.lessThan(expression, other);
            case LE -> builder.lessThanOrEqualTo(expression, other);
        };
    }

    private Expression<Comparable> comparable(Operand operand, Expression<?> target) {
        if (operand instanceof Operand.Value value) {
            return builder.literal((Comparable) coerce(value.value(), target.getJavaType()));
        }
        return expression(operand);
    }

    <T> Expression<T> expression(Operand operand) {
        if (operand instanceof Operand.FieldRef field) {
            return (Expression<T>) path(field.path());
        }
        if (operand instanceof Operand.Value value) {
            return (Expression<T>) builder.literal(value.value());
        }
        Operand.Call call = (Operand.Call) operand;
        List<Operand> arguments = call.arguments();
        return (Expression<T>) switch (call.function()) {
            case LOWER -> builder.lower(expression(arguments.get(0)));
            case UPPER -> builder.upper(expression(arguments.get(0)));
            case TRIM -> builder.trim(expression(arguments.get(0)));
            case LENGTH -> builder.length(expression(arguments.get(0)));
            case SIZE -> builder.size((Expression<Collection<?>>) (Expression) expression(arguments.get(0)));
            case NEG -> builder.neg(expression(arguments.get(0)));
            case SUM -> builder.sum(number(arguments.get(0), arguments.get(1)), number(arguments.get(1), arguments.get(0)));
            case DIFF -> builder.diff(number(arguments.get(0), arguments.get(1)), number(arguments.get(1), arguments.get(0)));
            case PROD -> builder.prod(number(arguments.get(0), arguments.get(1)), number(arguments.get(1), arguments.get(0)));
            case QUOT -> builder.quot(number(arguments.get(0), arguments.get(1)), number(arguments.get(1), arguments.get(0)));
            case MOD -> builder.mod((Expression<Integer>) (Expression) number(arguments.get(0), arguments.get(1)),
                    (Expression<Integer>) (Expression) number(arguments.get(1), arguments.get(0)));
            case CONCAT -> concat(arguments);
        };
    }

    private Expression<String> concat(List<Operand> arguments) {
        Expression<String> result = expression(arguments.get(0));
        for (Operand argument : arguments.subList(1, arguments.size())) {
            Expression<String> next = expression(argument);
            result = builder.concat(result, next);
        }
        return result;
    }

    /** Numeric operand; a value is converted to the type of the other operand. */
    private Expression<Number> number(Operand operand, Operand other) {
        if (operand instanceof Operand.Value value && !(other instanceof Operand.Value)) {
            return builder.literal((Number) coerce(value.value(), expression(other).getJavaType()));
        }
        return expression(operand);
    }

    Path<?> path(String path) {
        if (path.isEmpty()) {
            throw new QueryForgeUnsupportedExpressionException("The model itself can not be used in a condition, use one of its fields");
        }
        String[] segments = path.split("\\.");
        From<?, ?> from = root;
        Path<?> embedded = null;
        ManagedType<?> type = root.getModel();
        for (int i = 0; i < segments.length - 1; i++) {
            Attribute<?, ?> attribute = type.getAttribute(segments[i]);
            if (attribute.isAssociation() && embedded == null) {
                From<?, ?> parent = from;
                boolean optional = optionalChain.getOrDefault(parent, false)
                        || !(attribute instanceof SingularAttribute<?, ?> singular) || singular.isOptional();
                from = joins.computeIfAbsent(parent, f -> new HashMap<>())
                        .computeIfAbsent(segments[i], name -> parent.join(name, optional ? JoinType.LEFT : JoinType.INNER));
                optionalChain.put(from, optional);
            } else if (attribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
                embedded = (embedded == null ? from : embedded).get(segments[i]);
            } else {
                throw new QueryForgeUnsupportedExpressionException("Can not navigate through " + segments[i] + " in " + path);
            }
            type = targetType(attribute, path);
        }
        return (embedded == null ? from : embedded).get(segments[segments.length - 1]);
    }

    private static ManagedType<?> targetType(Attribute<?, ?> attribute, String path) {
        Object target = attribute instanceof PluralAttribute<?, ?, ?> plural ? plural.getElementType()
                : ((SingularAttribute<?, ?>) attribute).getType();
        if (target instanceof ManagedType<?> managedType) {
            return managedType;
        }
        throw new QueryForgeUnsupportedExpressionException("Can not navigate through " + attribute.getName() + " in " + path);
    }

    /** Converts lambda values to the attribute type (an {@code int} literal for a {@code Long} id, ...). */
    static Object coerce(Object value, Class<?> target) {
        if (value == null || target == null) {
            return value;
        }
        Class<?> type = ClassUtils.resolvePrimitiveIfNecessary(target);
        if (type.isInstance(value)) {
            return value;
        }
        if (value instanceof Number number) {
            if (type == Long.class) {
                return number.longValue();
            }
            if (type == Integer.class) {
                return number.intValue();
            }
            if (type == Short.class) {
                return number.shortValue();
            }
            if (type == Byte.class) {
                return number.byteValue();
            }
            if (type == Double.class) {
                return number.doubleValue();
            }
            if (type == Float.class) {
                return number.floatValue();
            }
            if (type == BigDecimal.class) {
                return new BigDecimal(number.toString());
            }
            if (type == BigInteger.class) {
                return BigInteger.valueOf(number.longValue());
            }
            if (type == Character.class) {
                return (char) number.intValue();
            }
            if (type == Boolean.class) {
                return number.intValue() != 0;
            }
        }
        if (value instanceof String string && type.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) type, string);
        }
        return value;
    }
}
