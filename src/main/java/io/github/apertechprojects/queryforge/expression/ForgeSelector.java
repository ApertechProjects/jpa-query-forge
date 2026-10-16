package io.github.apertechprojects.queryforge.expression;

/**
 * Sort key over the generated query model: {@code orderBy(o -> o.supplier.name)}.
 * <p>
 * Extends {@link ForgeProperty} only so that methods overloaded for both pick this one for lambdas and the
 * {@code ForgeProperty} one for entity getter references; it is never used as a {@code ForgeProperty}.
 *
 * @param <Q> generated query model of the entity
 * @param <R> attribute type
 */
@FunctionalInterface
public interface ForgeSelector<Q, R> extends ForgeProperty<Object, R> {

    R select(Q model);

    @Override
    default R get(Object entity) {
        throw new UnsupportedOperationException("Selectors are translated, not called");
    }
}
