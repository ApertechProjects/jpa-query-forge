package io.github.apertechprojects.queryforge.expression;

import java.io.Serializable;

/**
 * Where condition over a generated query model, written as a plain Java lambda:
 * <pre>{@code
 * purchaseOrderRepository.where(o -> o.status == OrderStatus.APPROVED && o.supplier.name.startsWith("Azer"))
 * }</pre>
 * The lambda is never executed. Its bytecode is translated to a JPA predicate, so it may only contain:
 * <ul>
 *     <li>comparisons of model fields with values or other fields: {@code == != > >= < <=}</li>
 *     <li>{@code && || !}, {@code == null}, {@code != null}</li>
 *     <li>{@code equals}, {@code Objects.equals}, {@code compareTo}, {@code isAfter}, {@code isBefore}, {@code isEqual}</li>
 *     <li>{@code String} methods: {@code startsWith}, {@code endsWith}, {@code contains}, {@code equalsIgnoreCase},
 *     {@code isEmpty}, {@code toLowerCase}, {@code toUpperCase}, {@code trim}, {@code length}</li>
 *     <li>{@code values.contains(o.field)} on a collection value (SQL {@code IN})</li>
 *     <li>{@code o.collection.isEmpty()}, {@code o.collection.size()}</li>
 *     <li>arithmetic on numeric fields, and the functions of {@link F}</li>
 * </ul>
 * Anything that does not involve the model (captured variables, method calls on them, constants) is evaluated
 * while translating, so {@code o -> name == null || o.name.equals(name)} is a valid optional filter.
 *
 * @param <Q> generated query model of the entity, e.g. {@code QPurchaseOrder}
 */
@FunctionalInterface
public interface ForgePredicate<Q> extends Serializable {

    boolean test(Q model);
}
