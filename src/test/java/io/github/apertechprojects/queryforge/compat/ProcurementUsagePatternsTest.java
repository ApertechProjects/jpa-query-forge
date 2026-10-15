package io.github.apertechprojects.queryforge.compat;

import io.github.apertechprojects.queryforge.BaseTestInstance;
import io.github.apertechprojects.queryforge.TestApplication;
import io.github.apertechprojects.queryforge.annotation.EnableQueryForge;
import io.github.apertechprojects.queryforge.context.QueryForgeContext;
import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.criteria.CriteriaList;
import io.github.apertechprojects.queryforge.criteria.CriteriaOperator;
import io.github.apertechprojects.queryforge.criteria.Order;
import io.github.apertechprojects.queryforge.deserializer.DateTimeDeserializer;
import io.github.apertechprojects.queryforge.query.Forge;
import io.github.apertechprojects.queryforge.query.ForgeQuery;
import io.github.apertechprojects.queryforge.query.builder.DistinctWhereOrderByPage;
import io.github.apertechprojects.queryforge.query.builder.OrderByPage;
import io.github.apertechprojects.queryforge.query.builder.PageableResult;
import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.Customer;
import io.github.apertechprojects.queryforge.testenv.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.github.apertechprojects.queryforge.query.Forge.Field;
import static io.github.apertechprojects.queryforge.query.Forge.OR;
import static io.github.apertechprojects.queryforge.query.Forge.OrderBy;
import static io.github.apertechprojects.queryforge.query.Forge.Select;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mirrors how the library is used by existing applications (builder chains stored in typed variables,
 * select-into-DTO with join aliases, criteria lists, paging and partial consumption).
 */
@SpringBootTest(classes = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProcurementUsagePatternsTest extends BaseTestInstance {

    public static class CustomerView {
        private Long id;
        private String name;
        private Integer age;
        private Instant birthdate;
        private String userName;
        private Integer userAge;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }
        public Instant getBirthdate() { return birthdate; }
        public void setBirthdate(Instant birthdate) { this.birthdate = birthdate; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public Integer getUserAge() { return userAge; }
        public void setUserAge(Integer userAge) { this.userAge = userAge; }
    }

    private DistinctWhereOrderByPage<Customer, Long> selectCustomerView() {
        return customerRepository.queryBuilder()
                .select(
                        Select("id"),
                        Select("name"),
                        Select("age", "age"),
                        Select("user<name", "userName"),
                        Select("user.age", "userAge")
                );
    }

    private PageableResult<Customer, Long> customersFromAge(List<Criteria> criteriaList) {
        OrderByPage<Customer, Long> filtered = selectCustomerView().where(criteriaList.toArray(new Criteria[0]));
        return filtered.orderBy(OrderBy("age", Order.DESC));
    }

    @Test
    void selectWithJoinAliasesIntoDto() {
        List<CustomerView> result = customersFromAge(List.of(Criteria.of("age", CriteriaOperator.GREATER_THAN_OR_EQUAL, 24)))
                .getResult(CustomerView.class);

        assertEquals(List.of(27, 26, 25, 24), result.stream().map(CustomerView::getAge).toList());
        assertEquals("Name 7", result.get(1).getUserName());
        assertEquals(41, result.get(1).getUserAge());
        assertEquals(customer7.getId(), result.get(1).getId());
        assertNull(result.get(0).getName());
    }

    @Test
    void pagedResult() {
        Page<CustomerView> page = customersFromAge(new ArrayList<>()).page(1, 3).getResultAsPage(CustomerView.class);

        assertEquals(8, page.getTotalElements());
        assertEquals(List.of(24, 23, 22), page.getContent().stream().map(CustomerView::getAge).toList());
    }

    @Test
    void distinctSelect() {
        List<CustomerView> result = customerRepository.queryBuilder()
                .select(Select("user.surname", "userName"))
                .distinct(true)
                .where(Field("age").greaterThan(0))
                .getResult(CustomerView.class);

        assertEquals(1, result.size());
        assertEquals("Surname 1", result.get(0).getUserName());
    }

    @Test
    void selectRuleArrayAndEntityResult() {
        Forge.SelectRule[] rules = {Select("id"), Select("name")};
        List<Customer> customers = customerRepository.queryBuilder()
                .select(rules)
                .where(Criteria.of("user.status", CriteriaOperator.EQUAL, User.Status.ACTIVE))
                .orderBy(OrderBy("id", Order.ASC))
                .getResult();

        assertEquals(List.of(customer2.getId(), customer4.getId(), customer6.getId(), customer7.getId()),
                customers.stream().map(Customer::getId).toList());
    }

    @Test
    void criteriaListFindAllCountAndPageable() {
        CriteriaList criteria = CriteriaList.of(
                Criteria.of("user.type", CriteriaOperator.EQUAL, "ADMIN"),
                Criteria.of("age", CriteriaOperator.LESS_THAN_OR_EQUAL, 25));

        assertEquals(List.of(customer2.getId(), customer5.getId(), customer6.getId()),
                customerRepository.findAll(criteria).stream().map(Customer::getId).sorted().toList());
        assertEquals(3, customerRepository.count(criteria));

        Page<Customer> page = customerRepository.findAll(criteria, PageRequest.of(0, 2));
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());
    }

    @Test
    void parenthesesOrAndSpecified() {
        List<Criteria> inner = List.of(Criteria.of("age", CriteriaOperator.EQUAL, 20), OR, Criteria.of("age", CriteriaOperator.EQUAL, 21));
        List<Customer> result = customerRepository.findAll(List.of(
                Criteria.of("", CriteriaOperator.PARENTHES, inner),
                OR,
                Field("name").isNull()));

        assertEquals(List.of(customer1.getId(), customer2.getId(), customer8.getId()),
                result.stream().map(Customer::getId).sorted().toList());

        assertEquals(3, customerRepository.queryBuilder()
                .where(Forge.Parenthesis(Field("age").eq(20), OR, Field("age").eq(21)), OR, Field("name").isNull())
                .getResult().size());
    }

    @Test
    void projectionWithoutSelectUsesBasicAttributesByName() {
        List<CustomerView> result = customerRepository.findAll(ForgeQuery.of(List.of(Field("age").lessThan(22))), CustomerView.class);

        assertEquals(List.of(20, 21), result.stream().map(CustomerView::getAge).sorted().toList());
        result.forEach(view -> assertEquals(view.getAge() == 20 ? "Customer 1" : "Customer 2", view.getName()));
    }

    @Test
    @Transactional
    void projectionWithoutSelectKeepsRowsWithNullAssociations() {
        customerRepository.save(new Customer(null, "Orphan", 99, Instant.now(), null));

        List<CustomerView> result = customerRepository.findAll(ForgeQuery.of(List.of(Field("age").eq(99))), CustomerView.class);

        assertEquals(List.of("Orphan"), result.stream().map(CustomerView::getName).toList());
    }

    @Test
    void consumePartially() {
        List<Long> consumed = new ArrayList<>();
        customerRepository.consumePartially(List.of(Field("age").greaterThanOrEqual(22)), list -> list.forEach(c -> consumed.add(c.getId())), 4);
        assertEquals(6, consumed.size());

        List<Long> bySpecification = new ArrayList<>();
        // the first page must be filtered too (upstream applied the specification only from the second page on)
        Specification<Customer> specification = JpaQueryForgeRepository.createSpecification(List.of(Field("age").greaterThanOrEqual(23)));
        customerRepository.consumePartially(specification, list -> list.forEach(c -> bySpecification.add(c.getId())), 2);
        assertEquals(List.of(customer4.getId(), customer5.getId(), customer6.getId(), customer7.getId(), customer8.getId()),
                bySpecification.stream().sorted().toList());
    }

    @Test
    void customDeserializerBeanIsUsed() {
        assertInstanceOf(DateTimeDeserializer.class, QueryForgeContext.repositoryContext().getDeserializer());
    }

    @Test
    void enableAnnotationDoesNotTouchRepositoryScanning() {
        assertFalse(AnnotatedElementUtils.hasAnnotation(EnableQueryForge.class, EnableJpaRepositories.class));
    }
}
