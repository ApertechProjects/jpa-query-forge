package io.github.apertechprojects.queryforge.internal.expression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Boolean part of a translated where lambda. Build with {@link #and}, {@link #or} and {@link #not}: they fold
 * constants and push negations down to the leaves (comparisons are negated by flipping the operator).
 */
public sealed interface Formula {

    Formula TRUE = new Constant(true);
    Formula FALSE = new Constant(false);

    record Constant(boolean value) implements Formula {
    }

    record And(List<Formula> parts) implements Formula {
    }

    record Or(List<Formula> parts) implements Formula {
    }

    /** Negation of a leaf that has no negated form of its own. */
    record Not(Formula formula) implements Formula {
    }

    /** A value on the left is moved to the right: {@code 18 < age} is stored as {@code age > 18}. */
    record Comparison(Operator operator, Operand left, Operand right) implements Formula {
        public Comparison {
            if (left instanceof Operand.Value && !(right instanceof Operand.Value)) {
                Operand value = left;
                left = right;
                right = value;
                operator = operator.flip();
            }
        }
    }

    record IsNull(Operand operand) implements Formula {
    }

    /** {@code escape} is {@code null} for raw patterns ({@code F.like}). */
    record Like(Operand operand, String pattern, Character escape) implements Formula {
    }

    record In(Operand operand, Collection<?> values) implements Formula {
    }

    /** {@code value} is an element of the collection attribute {@code collection}. */
    record IsMember(Object value, Operand collection) implements Formula {
    }

    record Between(Operand operand, Operand from, Operand to) implements Formula {
    }

    record IsEmpty(Operand collection) implements Formula {
    }

    record IsTrue(Operand operand) implements Formula {
    }

    enum Operator {
        EQ, NE, GT, GE, LT, LE;

        public Operator negate() {
            return switch (this) {
                case EQ -> NE;
                case NE -> EQ;
                case GT -> LE;
                case GE -> LT;
                case LT -> GE;
                case LE -> GT;
            };
        }

        /** Operator for swapped operands: {@code a < b} is {@code b > a}. */
        public Operator flip() {
            return switch (this) {
                case EQ, NE -> this;
                case GT -> LT;
                case GE -> LE;
                case LT -> GT;
                case LE -> GE;
            };
        }

        public boolean test(int compareResult) {
            return switch (this) {
                case EQ -> compareResult == 0;
                case NE -> compareResult != 0;
                case GT -> compareResult > 0;
                case GE -> compareResult >= 0;
                case LT -> compareResult < 0;
                case LE -> compareResult <= 0;
            };
        }
    }

    static Formula and(Formula left, Formula right) {
        if (left.equals(FALSE) || right.equals(FALSE)) {
            return FALSE;
        }
        if (left.equals(TRUE)) {
            return right;
        }
        if (right.equals(TRUE)) {
            return left;
        }
        List<Formula> parts = new ArrayList<>();
        addFlattened(parts, left, And.class);
        addFlattened(parts, right, And.class);
        return new And(List.copyOf(parts));
    }

    static Formula or(Formula left, Formula right) {
        if (left.equals(TRUE) || right.equals(TRUE)) {
            return TRUE;
        }
        if (left.equals(FALSE)) {
            return right;
        }
        if (right.equals(FALSE)) {
            return left;
        }
        List<Formula> parts = new ArrayList<>();
        addFlattened(parts, left, Or.class);
        addFlattened(parts, right, Or.class);
        return new Or(List.copyOf(parts));
    }

    static Formula not(Formula formula) {
        if (formula instanceof Constant constant) {
            return constant.value() ? FALSE : TRUE;
        }
        if (formula instanceof Not not) {
            return not.formula();
        }
        if (formula instanceof Comparison comparison) {
            return new Comparison(comparison.operator().negate(), comparison.left(), comparison.right());
        }
        if (formula instanceof And and) {
            return and.parts().stream().map(Formula::not).reduce(FALSE, Formula::or);
        }
        if (formula instanceof Or or) {
            return or.parts().stream().map(Formula::not).reduce(TRUE, Formula::and);
        }
        return new Not(formula);
    }

    /** {@code condition ? whenTrue : whenFalse} as a formula, without repeating the condition where possible. */
    static Formula choose(Formula condition, Formula whenTrue, Formula whenFalse) {
        if (whenTrue.equals(whenFalse)) {
            return whenTrue;
        }
        if (whenTrue.equals(TRUE)) {
            return or(condition, whenFalse);
        }
        if (whenTrue.equals(FALSE)) {
            return and(not(condition), whenFalse);
        }
        if (whenFalse.equals(TRUE)) {
            return or(not(condition), whenTrue);
        }
        if (whenFalse.equals(FALSE)) {
            return and(condition, whenTrue);
        }
        return or(and(condition, whenTrue), and(not(condition), whenFalse));
    }

    private static void addFlattened(List<Formula> parts, Formula formula, Class<? extends Formula> type) {
        if (type == And.class && formula instanceof And and) {
            parts.addAll(and.parts());
        } else if (type == Or.class && formula instanceof Or or) {
            parts.addAll(or.parts());
        } else {
            parts.add(formula);
        }
    }
}
