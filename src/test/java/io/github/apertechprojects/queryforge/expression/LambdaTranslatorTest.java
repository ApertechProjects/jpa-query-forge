package io.github.apertechprojects.queryforge.expression;

import io.github.apertechprojects.queryforge.exception.QueryForgeUnsupportedExpressionException;
import io.github.apertechprojects.queryforge.internal.expression.Formula;
import io.github.apertechprojects.queryforge.internal.expression.Formula.Comparison;
import io.github.apertechprojects.queryforge.internal.expression.Formula.Operator;
import io.github.apertechprojects.queryforge.internal.expression.LambdaTranslator;
import io.github.apertechprojects.queryforge.internal.expression.Operand.FieldRef;
import io.github.apertechprojects.queryforge.internal.expression.Operand.Value;
import io.github.apertechprojects.queryforge.testenv.entity.Customer;
import io.github.apertechprojects.queryforge.testenv.entity.QCustomer;
import io.github.apertechprojects.queryforge.testenv.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaTranslatorTest {

    private static final Formula AGE_GT_18 = new Comparison(Operator.GT, new FieldRef("age"), new Value(18));
    private static final Formula ACTIVE = new Comparison(Operator.EQ, new FieldRef("user.status"), new Value(User.Status.ACTIVE));
    private static final Formula NAME_IS_A = new Comparison(Operator.EQ, new FieldRef("name"), new Value("A"));

    private int minimumAge = 30;

    private static Formula translate(ForgePredicate<QCustomer> predicate) {
        return LambdaTranslator.translate(predicate);
    }

    @Test
    void comparison() {
        assertEquals(AGE_GT_18, translate(c -> c.age > 18));
        assertEquals(new Comparison(Operator.LT, new FieldRef("age"), new Value(18)), translate(c -> 18 > c.age));
    }

    @Test
    void andOrKeepSourceStructure() {
        assertEquals(new Formula.And(List.of(AGE_GT_18, ACTIVE)), translate(c -> c.age > 18 && c.user.status == User.Status.ACTIVE));
        assertEquals(new Formula.Or(List.of(AGE_GT_18, ACTIVE)), translate(c -> c.age > 18 || c.user.status == User.Status.ACTIVE));
        assertEquals(new Formula.And(List.of(new Formula.Or(List.of(NAME_IS_A, AGE_GT_18)), ACTIVE)),
                translate(c -> (c.name.equals("A") || c.age > 18) && c.user.status == User.Status.ACTIVE));
        assertEquals(new Formula.Or(List.of(new Formula.And(List.of(NAME_IS_A, AGE_GT_18)), ACTIVE)),
                translate(c -> c.name.equals("A") && c.age > 18 || c.user.status == User.Status.ACTIVE));
    }

    @Test
    void negationIsPushedToTheLeaves() {
        assertEquals(new Comparison(Operator.LE, new FieldRef("age"), new Value(18)), translate(c -> !(c.age > 18)));
        assertEquals(new Formula.Or(List.of(
                        new Comparison(Operator.LE, new FieldRef("age"), new Value(18)),
                        new Comparison(Operator.NE, new FieldRef("user.status"), new Value(User.Status.ACTIVE)))),
                translate(c -> !(c.age > 18 && c.user.status == User.Status.ACTIVE)));
    }

    @Test
    void nullChecks() {
        assertEquals(new Formula.IsNull(new FieldRef("name")), translate(c -> c.name == null));
        assertEquals(new Formula.Not(new Formula.IsNull(new FieldRef("user"))), translate(c -> c.user != null));
    }

    @Test
    void capturedValuesAreEvaluated() {
        String nullName = null;
        assertEquals(Formula.TRUE, translate(c -> nullName == null || c.name.equals(nullName)));

        String name = "A";
        assertEquals(NAME_IS_A, translate(c -> name == null || c.name.equals(name)));

        Filter filter = new Filter(18, List.of(1L, 2L));
        assertEquals(new Formula.And(List.of(AGE_GT_18, new Formula.In(new FieldRef("id"), List.of(1L, 2L)))),
                translate(c -> c.age > filter.minAge() && filter.ids().contains(c.id)));

        assertEquals(new Comparison(Operator.GE, new FieldRef("age"), new Value(30)), translate(c -> c.age >= minimumAge));

        java.util.ArrayList<Long> arrayList = new java.util.ArrayList<>(List.of(1L, 2L));
        java.util.LinkedHashSet<Long> linkedSet = new java.util.LinkedHashSet<>(List.of(3L));
        assertEquals(new Formula.In(new FieldRef("id"), List.of(1L, 2L)), translate(c -> arrayList.contains(c.id)));
        assertEquals(new Formula.In(new FieldRef("id"), linkedSet), translate(c -> linkedSet.contains(c.id)));
        assertEquals(new Comparison(Operator.GT, new FieldRef("age"), new Value(20)), translate(c -> c.age > filter.minAge() + 2));
    }

    @Test
    void stringMethods() {
        assertEquals(new Formula.Like(new FieldRef("name"), "Cus%", '\\'), translate(c -> c.name.startsWith("Cus")));
        assertEquals(new Formula.Like(new FieldRef("name"), "%100\\%%", '\\'), translate(c -> c.name.contains("100%")));
        String prefix = "Cu";
        assertEquals(new Formula.Like(new FieldRef("name"), "%Cu_x", null), translate(c -> F.like(c.name, "%" + prefix + "_x")));
    }

    @Test
    void compareToAndDates() {
        Integer limit = 10;
        assertEquals(new Comparison(Operator.GT, new FieldRef("age"), new Value(limit)), translate(c -> c.age.compareTo(limit) > 0));
        Instant now = Instant.now();
        assertEquals(new Comparison(Operator.LT, new FieldRef("birthdate"), new Value(now)), translate(c -> c.birthdate.isBefore(now)));
        assertEquals(new Comparison(Operator.GE, new FieldRef("birthdate"), new Value(now)), translate(c -> c.birthdate.compareTo(now) >= 0));
    }

    @Test
    void sortKeys() {
        assertEquals(new FieldRef("user.name"), LambdaTranslator.translateSelector((ForgeSelector<QCustomer, ?>) c -> c.user.name));
        assertEquals(new FieldRef("age"), LambdaTranslator.translateSelector((ForgeSelector<QCustomer, ?>) c -> c.age));
        assertEquals(new FieldRef("age"), LambdaTranslator.translateSelector((ForgeProperty<Customer, ?>) Customer::getAge));
        assertEquals(new FieldRef("birthdate"), LambdaTranslator.translateSelector((ForgeProperty<Customer, ?>) Customer::getBirthdate));
        assertThrows(QueryForgeUnsupportedExpressionException.class,
                () -> LambdaTranslator.translateSelector((ForgeSelector<QCustomer, ?>) c -> c.age > 5 ? c.name : c.user.name));
    }

    @Test
    void unsupportedExpressionsFailWithTheLocation() {
        QueryForgeUnsupportedExpressionException exception = assertThrows(QueryForgeUnsupportedExpressionException.class,
                () -> translate(c -> c.name.hashCode() == 5));
        assertTrue(exception.getMessage().contains(LambdaTranslatorTest.class.getName() + " line "), exception.getMessage());
        assertTrue(exception.getMessage().contains("java.lang.String.hashCode"), exception.getMessage());
    }

    record Filter(Integer minAge, List<Long> ids) {
    }
}
