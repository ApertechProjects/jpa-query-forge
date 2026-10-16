package io.github.apertechprojects.queryforge.expression;

import io.github.apertechprojects.queryforge.BaseTestInstance;
import io.github.apertechprojects.queryforge.TestApplication;
import io.github.apertechprojects.queryforge.exception.QueryForgeUnsupportedExpressionException;
import io.github.apertechprojects.queryforge.query.ForgeQueryable;
import io.github.apertechprojects.queryforge.testenv.entity.Customer;
import io.github.apertechprojects.queryforge.testenv.entity.QCustomer;
import io.github.apertechprojects.queryforge.testenv.entity.User;
import io.github.apertechprojects.queryforge.testenv.entity.school.Course;
import io.github.apertechprojects.queryforge.testenv.entity.school.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WhereLambdaTest extends BaseTestInstance {

    private final int minimumAge = 26;

    public record CustomerSummary(String name, String userName) {
    }

    // fixture ids are only set when this class persisted the data (the context may be shared), ages are unique
    private static List<Integer> ages(Collection<Customer> customers) {
        return customers.stream().map(Customer::getAge).sorted().toList();
    }

    private static List<Integer> ages(Customer... customers) {
        return ages(List.of(customers));
    }

    private List<Integer> where(ForgePredicate<QCustomer> predicate) {
        return ages(customerRepository.where(predicate).toList());
    }

    private Long customerId(int age) {
        return customerRepository.firstOrDefault(c -> c.age == age).orElseThrow().getId();
    }

    private static List<String> studentNames(List<Student> students) {
        return students.stream().map(Student::getName).sorted().toList();
    }

    private static List<String> courseNames(List<Course> courses) {
        return courses.stream().map(Course::getName).sorted().toList();
    }

    @Test
    void andWithNavigation() {
        assertEquals(ages(customer4, customer6, customer7), where(c -> c.age > 22 && c.user.status == User.Status.ACTIVE));
    }

    @Test
    void orKeepsRowsWithNullValues() {
        // customer8 has no name: name LIKE ... is NULL, the other side of || must still match it
        assertEquals(ages(customer1, customer7, customer8), where(c -> c.name.startsWith("Customer 1") || c.user.age >= 41));
        assertEquals(ages(customer8), where(c -> (c.name.equals("Customer 1") || c.age > 25) && c.user.type == User.Type.ADMIN));
    }

    @Test
    void negationAndNullChecks() {
        assertEquals(ages(customer6, customer7, customer8), where(c -> !(c.age < 25)));
        assertEquals(ages(customer8), where(c -> c.name == null));
        assertEquals(7, customerRepository.count(c -> c.name != null));
    }

    @Test
    void optionalFiltersFromCapturedValues() {
        String noName = null;
        assertEquals(8, where(c -> noName == null || c.name.equals(noName)).size());
        String name = "Customer 3";
        assertEquals(ages(customer3), where(c -> name == null || c.name.equals(name)));

        ForgeQueryable<Customer, QCustomer> query = customerRepository.where(c -> c.age >= 22);
        User.Status status = User.Status.PASSIVE;
        if (status != null) {
            query = query.where(c -> c.user.status == status);
        }
        assertEquals(ages(customer3, customer5, customer8), ages(query.toList()));
    }

    @Test
    void capturedCollectionsObjectsAndFields() {
        List<Integer> ages = List.of(20, 22);
        assertEquals(ages(customer1, customer3), where(c -> ages.contains(c.age)));

        LambdaTranslatorTest.Filter filter = new LambdaTranslatorTest.Filter(24, List.of(customerId(25), customerId(21)));
        assertEquals(ages(customer6), where(c -> c.age > filter.minAge() && filter.ids().contains(c.id)));

        assertEquals(ages(customer7, customer8), where(c -> c.age >= minimumAge));

        long id = customerId(21);
        assertEquals(ages(customer2), where(c -> c.id == id));
        Long userId = userRepository.firstOrDefault(u -> u.name.equals("Name 2")).orElseThrow().getId();
        assertEquals(ages(customer2), where(c -> c.user.id.equals(userId)));
    }

    @Test
    void stringFunctions() {
        assertEquals(ages(customer2), where(c -> c.name.toLowerCase().contains("tomer 2")));
        assertEquals(ages(customer5), where(c -> c.name.endsWith("5")));
        assertEquals(ages(customer4), where(c -> c.name.equalsIgnoreCase("CUSTOMER 4")));
        assertEquals(7, where(c -> F.like(c.name, "Customer _")).size());
        assertEquals(ages(customer1), where(c -> F.notLike(c.name, "Customer _") || c.name.endsWith("1")));
    }

    @Test
    void datesBetweenAndArithmetic() {
        // fixture birthdates shift per test instance, so read the persisted one
        Instant threshold = customerRepository.firstOrDefault(c -> c.age == 23).orElseThrow().getBirthdate().minus(Duration.ofDays(1));
        assertEquals(ages(customer5, customer6, customer7, customer8), where(c -> c.birthdate.isBefore(threshold)));
        assertEquals(ages(customer1, customer2, customer3, customer4), where(c -> c.birthdate.compareTo(threshold) > 0));
        assertEquals(ages(customer3, customer4, customer5), where(c -> F.between(c.age, 22, 24)));
        assertEquals(ages(customer5, customer6, customer7, customer8), where(c -> c.age + 10 > 33));
    }

    @Test
    void terminalOperations() {
        assertEquals(3, customerRepository.count(c -> c.user.type == User.Type.ADMIN && c.age < 26));
        assertTrue(customerRepository.any(c -> c.name.equals("Customer 6")));
        assertFalse(customerRepository.any(c -> c.name.equals("nobody")));
        assertEquals(25, customerRepository.firstOrDefault(c -> c.name.equals("Customer 6")).orElseThrow().getAge());
        assertTrue(customerRepository.firstOrDefault(c -> c.age > 100).isEmpty());
    }

    @Test
    void queriesAreImmutable() {
        ForgeQueryable<Customer, QCustomer> adults = customerRepository.where(c -> c.age >= 24);
        ForgeQueryable<Customer, QCustomer> active = adults.where(c -> c.user.status == User.Status.ACTIVE);
        assertEquals(4, adults.count());
        assertEquals(2, active.count());
        assertEquals(1, active.count(c -> c.age > 25));
    }

    @Test
    void selectProjection() {
        List<CustomerSummary> rows = customerRepository.where(c -> c.age < 22).select(c -> new CustomerSummary(c.name, c.user.name)).toList();
        assertEquals(List.of("Customer 1:Name 1", "Customer 2:Name 2"),
                rows.stream().map(r -> r.name() + ":" + r.userName()).sorted().toList());
    }

    @Test
    void optionalAssociationsCollectionsAndBooleans() {
        assertEquals(List.of("Robert Johnson"), studentNames(studentRepository.where(s -> s.department.name.equals("Physics")).toList()));
        assertEquals(List.of("Student Eleven"), studentNames(studentRepository.where(s -> s.address == null).toList()));
        assertEquals(List.of("Student Eleven"), studentNames(studentRepository.where(s -> s.courses.isEmpty()).toList()));
        assertEquals(List.of("Jane Smith", "John Doe"), studentNames(studentRepository.where(s -> s.courses.size() > 1).toList()));
        // Student Eleven has no department: the LEFT join keeps it for the || branch
        assertEquals(List.of("Robert Johnson", "Student Eleven"),
                studentNames(studentRepository.where(s -> s.department.name.equals("Physics") || s.department == null).toList()));

        assertEquals(List.of("Biology I", "Biology II", "Calculus I", "Introduction to Computer Science"),
                courseNames(courseRepository.where(c -> c.active).toList()));
        assertEquals(List.of("English Literature I"), courseNames(courseRepository.where(c -> !c.active).toList()));
    }

    @Test
    void unsupportedLambdaFailsWhenTheConditionIsAdded() {
        assertThrows(QueryForgeUnsupportedExpressionException.class, () -> customerRepository.where(c -> c.name.hashCode() == 1));
    }
}
