package io.github.apertechprojects.queryforge.internal.expression;

import java.util.List;

/**
 * Non-boolean part of a translated where lambda.
 */
public sealed interface Operand {

    /** Attribute path of the query model, e.g. {@code supplier.name}. */
    record FieldRef(String path) implements Operand {
    }

    /** Value computed while translating (constant, captured variable, ...). */
    record Value(Object value) implements Operand {
    }

    record Call(Function function, List<Operand> arguments) implements Operand {

        public Call(Function function, Operand... arguments) {
            this(function, List.of(arguments));
        }
    }

    enum Function {
        LOWER, UPPER, TRIM, LENGTH, SIZE, SUM, DIFF, PROD, QUOT, MOD, NEG, CONCAT
    }
}
