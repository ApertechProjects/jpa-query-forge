package io.github.apertechprojects.queryforge.expression;

import io.github.apertechprojects.queryforge.BaseTestInstance;
import io.github.apertechprojects.queryforge.TestApplication;
import io.github.apertechprojects.queryforge.testenv.entity.Customer;
import io.github.apertechprojects.queryforge.testenv.entity.User;
import io.github.apertechprojects.queryforge.testenv.entity.school.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderingAndPagingTest extends BaseTestInstance {

    // ages are unique in the fixture (20..27), so they identify the customers
    private static List<Integer> ages(List<Customer> customers) {
        return customers.stream().map(Customer::getAge).toList();
    }

    @Test
    void orderByEntityGetter() {
        assertEquals(List.of(27, 26, 25, 24, 23, 22, 21, 20), ages(customerRepository.orderByDescending(Customer::getAge).toList()));
        assertEquals(List.of(23, 24, 25), ages(customerRepository.where(c -> c.age > 22 && c.age < 26).orderBy(Customer::getAge).toList()));
    }

    @Test
    void orderByModelLambdaThenBy() {
        // every user has surname "Surname 1", so the second key decides
        assertEquals(List.of(27, 26, 25, 24, 23, 22, 21, 20),
                ages(customerRepository.where(c -> c.age > 0).orderBy(c -> c.user.surname).thenByDescending(c -> c.age).toList()));
        assertEquals(List.of(20, 21, 22, 23, 24, 25, 26, 27),
                ages(customerRepository.orderBy(c -> c.user.surname).thenBy(Customer::getAge).toList()));
        assertEquals(List.of(21, 23, 25, 26, 20, 22, 24, 27),
                ages(customerRepository.orderBy(c -> c.user.status).thenBy(c -> c.user.age).toList()));
    }

    @Test
    void orderByReplacesThePreviousOrderAndWhereKeepsIt() {
        assertEquals(List.of(27, 26, 25), ages(customerRepository.orderBy(c -> c.name).orderByDescending(Customer::getAge).take(3).toList()));
        assertEquals(List.of(26, 23, 22, 20),
                ages(customerRepository.orderByDescending(Customer::getAge).where(c -> c.user.type == User.Type.USER).toList()));
    }

    @Test
    void navigationSortKeysKeepRowsWithoutTheAssociation() {
        List<Student> students = studentRepository.orderBy(s -> s.department.name).thenBy(Student::getName).toList();
        assertEquals(11, students.size());
    }

    @Test
    void pageAndTake() {
        assertEquals(List.of(23, 24, 25), ages(customerRepository.orderBy(Customer::getAge).page(1, 3).toList()));
        assertEquals(List.of(27, 26, 25), ages(customerRepository.orderByDescending(c -> c.age).take(3).toList()));
        assertEquals(List.of("Customer 7", "Customer 6"), customerRepository.where(c -> c.name != null)
                .orderByDescending(Customer::getAge).take(2)
                .select(c -> c.name).toList());
        assertThrows(IllegalArgumentException.class, () -> customerRepository.where(c -> c.age > 0).page(0, 0));
    }

    @Test
    void pagesWithoutOrderAreOrderedById() {
        List<Long> ids = customerRepository.where(c -> c.age > 0).page(0, 8).toList().stream().map(Customer::getId).toList();
        assertEquals(ids.stream().sorted().toList(), ids);
    }

    @Test
    void toPageWithPageable() {
        Page<Customer> page = customerRepository.where(c -> c.age >= 21).orderBy(Customer::getAge).toPage(PageRequest.of(1, 3));
        assertEquals(List.of(24, 25, 26), ages(page.getContent()));
        assertEquals(7, page.getTotalElements());
        assertEquals(3, page.getTotalPages());
        assertEquals(Sort.by(Sort.Direction.ASC, "age"), page.getSort());

        assertEquals(8, customerRepository.where(c -> c.age > 0).toPage(Pageable.unpaged()).getTotalElements());
    }

    @Test
    void toPageWithNumbersAndProjection() {
        Page<Customer> page = customerRepository.where(c -> c.user.status == User.Status.ACTIVE).orderByDescending(Customer::getAge).toPage(0, 3);
        assertEquals(List.of(26, 25, 23), ages(page.getContent()));
        assertEquals(4, page.getTotalElements());

        Page<WhereLambdaTest.CustomerSummary> summaries = customerRepository.where(c -> c.age < 24)
                .orderBy(c -> c.name).select(c -> new WhereLambdaTest.CustomerSummary(c.name, c.user.name)).toPage(PageRequest.of(0, 2));
        assertEquals(List.of("Customer 1:Name 1", "Customer 2:Name 2"),
                summaries.getContent().stream().map(s -> s.name() + ":" + s.userName()).toList());
        assertEquals(4, summaries.getTotalElements());
    }

    @Test
    void firstOrDefaultFollowsTheOrder() {
        assertEquals(27, customerRepository.orderByDescending(Customer::getAge).firstOrDefault().orElseThrow().getAge());
        assertEquals(21, customerRepository.where(c -> c.user.type == User.Type.ADMIN).orderBy(c -> c.age).firstOrDefault().orElseThrow().getAge());
    }
}
