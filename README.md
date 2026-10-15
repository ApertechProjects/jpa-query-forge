# JPA Query Forge

## Acknowledgements

JPA Query Forge started as a fork of
[spring-data-dynamic-query](https://github.com/tdilber/spring-data-dynamic-query). Many thanks to its
author and contributors for the original design and implementation.

## About

Dynamic, criteria-based querying for Spring Data JPA repositories.

Extend `JpaQueryForgeRepository` and build queries at runtime — filters, `AND`/`OR` groups, joins, `SELECT`
projections into DTOs, `DISTINCT`, `ORDER BY` and paging — without writing a query method or a
`Specification` per use case. Everything is translated to the JPA Criteria API, so it is as safe and
as fast as Spring Data JPA itself.

## Compatibility

| Spring Boot | Hibernate | Status |
|---|---|---|
| 3.0.x – 3.5.x | 6.x | tested (3.0.13, 3.2.12, 3.5.16) |
| 4.0.x – 4.1.x | 7.x | tested (4.0.8, 4.1.1) |

Java 17+. Only standard JPA APIs are used (no Hibernate internals).

## Installation

Available on Maven Central:

```xml
<dependency>
    <groupId>io.github.apertechprojects</groupId>
    <artifactId>jpa-query-forge</artifactId>
    <version>1.0.0</version>
</dependency>
```

Gradle: `implementation("io.github.apertechprojects:jpa-query-forge:1.0.0")`

With Spring Boot nothing else is required: auto-configuration registers everything.
`@EnableQueryForge` is available for explicit setups; unlike `@EnableJpaRepositories` it never changes
repository scanning, so it can live on any configuration class.

## Quick start

```java
public interface CustomerRepository extends JpaQueryForgeRepository<Customer, Long> {
}
```

### Criteria

```java
List<Customer> customers = customerRepository.findAll(CriteriaList.of(
        Criteria.of("age", CriteriaOperator.GREATER_THAN_OR_EQUAL, 18),
        Criteria.of("user.status", CriteriaOperator.EQUAL, "ACTIVE")));

Page<Customer> page = customerRepository.findAll(criteria, PageRequest.of(0, 20));
long total = customerRepository.count(criteria);
```

Operators: `CONTAIN`, `DOES_NOT_CONTAIN`, `START_WITH`, `END_WITH`, `EQUAL`, `NOT_EQUAL`,
`GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `SPECIFIED` (null check),
plus `OR` and `PARENTHES` (grouping). With several values, `EQUAL`, `CONTAIN`, `START_WITH` and `END_WITH`
match any of them; `NOT_EQUAL` and `DOES_NOT_CONTAIN` match none of them.

### Joins

The separator in a field path selects the join type: `.` inner, `<` left, `>` right.

```java
Criteria.of("department<name", CriteriaOperator.EQUAL, "Physics");   // LEFT JOIN department
```

### Query builder

```java
import static io.github.apertechprojects.queryforge.query.Forge.*;

List<CustomerView> rows = customerRepository.queryBuilder()
        .select(Select("id"), Select("name"), Select("user<name", "userName"))
        .distinct(false)
        .where(Field("age").greaterThan(18), OR, Parenthesis(Field("name").startWith("A"), Field("user.age").lessThan(30)))
        .orderBy(OrderBy("name", Order.ASC))
        .page(0, 20)
        .getResult(CustomerView.class);     // or getResult(), getResultAsPage(), getResultAsPage(Class)
```

Each step returns a narrower interface (`DistinctWhereOrderByPage`, `WhereOrderByPage`, `OrderByPage`,
`PageableResult`, `Result`), so partially built queries can be stored and reused.

Selected values are mapped onto the result class by alias (setter or record component name). Without
`select`, basic attributes of the entity are mapped by name.

### Annotated projections

```java
@ForgeModel
public class CustomerSummary {
    @ForgeField("user.name") private String userName;
    private Integer age;                 // selects "age"
    @ForgeIgnore private String note;    // not selected
    // getters/setters
}

List<CustomerSummary> list = customerRepository.findAll(new ForgeQuery(), CustomerSummary.class);
```

Records are supported; `@ForgeSubModel` maps nested objects.

### Web argument resolvers

`@EnableQueryForgeWebArgumentResolvers` lets controllers accept `CriteriaList` and `ForgeQuery` straight
from request parameters (`key0`, `operation0`, `values0`, `select0`, `orderBy0`, `orderByDirection0`,
`page`, `pageSize`, `distinct`).

### Batch processing

```java
customerRepository.consumePartially(criteria, batch -> process(batch), 500);
```

## Customization

- **Value conversion** — criteria values are converted to the attribute type by a `ValueDeserializer`.
  Declare a bean (typically extending `DefaultValueDeserializer`) to support more formats.
- **EntityManager** — the shared `EntityManager` of the primary `EntityManagerFactory` is used. Declare
  an `EntityManagerProvider` bean to change it.

## License

[Apache License 2.0](LICENSE)
