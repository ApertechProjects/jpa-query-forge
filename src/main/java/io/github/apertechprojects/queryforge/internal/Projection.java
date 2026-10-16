package io.github.apertechprojects.queryforge.internal;

import java.util.List;

/**
 * Turns the selected values of one row into a result object.
 *
 * @param <R> result type
 */
public interface Projection<R> {

    /** Attribute paths to select, e.g. {@code supplier.name}. */
    List<String> getPaths();

    /** Where the value of path {@code index} goes, for error messages. */
    String describe(int index);

    /** Fails before running the query when a selected type can not be assigned to its target. */
    default void check(List<Class<?>> selectedTypes) {
    }

    /** Result for one row; {@code values} follow {@link #getPaths()}. */
    R map(Object[] values);
}
