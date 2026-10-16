# JPA Query Forge

## Acknowledgements

JPA Query Forge started as a fork of
[spring-data-dynamic-query](https://github.com/tdilber/spring-data-dynamic-query). Many thanks to its
author and contributors for the original design and implementation.

## About

EF Core style, type-safe queries for Spring Data JPA repositories, written as plain Java lambdas:

```java
List<OrderRow> rows = purchaseOrderRepository
        .where(o -> o.status == OrderStatus.APPROVED && o.amount.compareTo(limit) > 0)
        .where(o -> o.supplier.name.startsWith("Azer") || o.supplier == null)
        .orderBy(o -> o.supplier.name).thenByDescending(PurchaseOrder::getCreatedAt)
        .select(OrderRow.class)
        .toList();
```

Lambdas are translated from their bytecode into the JPA Criteria API, so queries are checked by the compiler
and run as fast as hand-written Criteria queries.

## Compatibility

| Spring Boot | Hibernate | Java | Status |
|---|---|---|---|
| 4.0.x – 4.1.x | 7.x | 21+ | tested (4.0.8, 4.1.1) |

Only standard JPA APIs are used (no Hibernate internals).

## Installation

```xml
<dependency>
    <groupId>io.github.apertechprojects</groupId>
    <artifactId>jpa-query-forge</artifactId>
    <version>2.0.0</version>
</dependency>
```

The jar also contains the annotation processor that generates the query models. Register it next to your
other processors (Lombok, MapStruct, ...); JDK 23+ no longer runs processors found on the classpath
without this. The processor needs only the JDK, so its transitive dependencies can be excluded:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.github.apertechprojects</groupId>
                <artifactId>jpa-query-forge</artifactId>
                <version>2.0.0</version>
                <exclusions>
                    <exclusion>
                        <groupId>*</groupId>
                        <artifactId>*</artifactId>
                    </exclusion>
                </exclusions>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Gradle: `implementation(...)` and `annotationProcessor("io.github.apertechprojects:jpa-query-forge:2.0.0")`.

With Spring Boot nothing else is required: auto-configuration registers everything. `@EnableQueryForge` is
available for explicit setups; it never changes repository scanning.

## Query models

For every `@Entity` and `@Embeddable` the processor generates a query model with the same persistent fields
(including those of `@MappedSuperclass` parents), all public. `Customer` gets `QCustomer`:

```java
public final class QCustomer implements QueryModel {
    public String id;
    public String name;
    public BigDecimal balance;
    public LocalDate birthDate;
    public QUser user;              // associations point to their query models
    public List<QOrder> orders;
}
```

Repositories name the model as third type argument:

```java
public interface CustomerRepository extends JpaQueryForgeRepository<Customer, String, QCustomer> {
}
```

## Where

```java
List<Customer> customers = customerRepository
        .where(c -> c.balance.compareTo(BigDecimal.ZERO) > 0 && c.user.status == Status.ACTIVE)
        .where(c -> c.name.startsWith("A") || c.user == null)
        .toList();

long count            = customerRepository.count(c -> c.birthDate.isAfter(from));
boolean exists        = customerRepository.any(c -> c.email.equals(email));
Optional<Customer> c  = customerRepository.firstOrDefault(c -> c.id.equals(id));
```

Queries are immutable, so they can be extended conditionally:

```java
ForgeQueryable<Customer, QCustomer> query = customerRepository.where(c -> c.deletedDate == null);
if (filter.status() != null) {
    query = query.where(c -> c.user.status == filter.status());
}
```

Conditions that do not involve the model are evaluated while translating, so optional filters can also be
written inline: `c -> name == null || c.name.equals(name)`, `c -> ids.isEmpty() || ids.contains(c.id)`.

Supported inside a where lambda:

| Java | SQL |
|---|---|
| `== != > >= < <=` on numbers, enums, booleans, references | comparison |
| `&&`, `\|\|`, `!` | `AND`, `OR`, `NOT` (grouping as written) |
| `x == null`, `x != null`, `Objects.isNull/nonNull` | `IS NULL`, `IS NOT NULL` |
| `equals`, `Objects.equals` | `=` |
| `compareTo(v) > 0`, `isAfter`, `isBefore`, `isEqual` | comparison (`BigDecimal`, dates, strings) |
| `add`, `subtract`, `multiply`, `divide`, `negate` on `BigDecimal`; `+ - * / %` on numbers | arithmetic |
| `startsWith`, `endsWith`, `contains` | `LIKE` (wildcards in the value are escaped) |
| `equalsIgnoreCase`, `toLowerCase`, `toUpperCase`, `trim`, `length`, `isEmpty`, `isBlank` | `LOWER`, `UPPER`, `TRIM`, `LENGTH` |
| `values.contains(c.field)` | `IN` |
| `c.collection.isEmpty()`, `c.collection.size()`, `c.collection.contains(v)` | `IS EMPTY`, `SIZE`, `MEMBER OF` |
| `F.like(c.name, "A%")`, `F.notLike`, `F.between(c.age, 18, 30)` | `LIKE`, `NOT LIKE`, `BETWEEN` |
| boolean field: `c -> c.active`, `c -> !c.active` | `= TRUE`, `= FALSE` |

## Ordering and paging

```java
Page<Customer> page = customerRepository
        .where(c -> c.user.status == Status.ACTIVE)
        .orderBy(c -> c.user.name)                  // lambda over the model, navigations allowed
        .thenByDescending(Customer::getBirthDate)   // or an entity getter
        .toPage(pageable);                          // or toPage(page, size)

List<Customer> rows  = customerRepository.orderByDescending(Customer::getBirthDate).page(2, 20).toList();
List<Customer> top10 = customerRepository.orderBy(c -> c.name).take(10).toList();
```

- `orderBy`/`orderByDescending` replace the current order; `thenBy`/`thenByDescending` are only available
  right after them (like EF Core's `IOrderedQueryable`).
- `toPage(pageable)` uses page number and size of the `Pageable`; its sort is not applied — use `orderBy`.
- Paged queries without `orderBy` are ordered by id, so pages do not overlap.

## Select

### Into a `@ForgeModel` class

`select(Dto.class)` maps each row onto a class annotated with `@ForgeModel`. The class says what is selected;
the query code stays free of mapping:

```java
@Getter @Setter
@ForgeModel(entity = Customer.class)
public class CustomerRow {
    private String id;                              // attribute of the same name
    private String name;
    @ForgeField("user.name")
    private String userName;                        // path through an association
    @ForgeField("status")
    private String statusName;                      // any value can be read as String (enums by name)
    @ForgeSubModel("address")
    private AddressPart address;                    // nested @ForgeModel, paths relative to "address"
    @ForgeIgnore
    private String label;                           // not selected, filled later
}

Page<CustomerRow> page = customerRepository.where(c -> c.balance.compareTo(BigDecimal.ZERO) > 0)
        .orderBy(c -> c.name)
        .select(CustomerRow.class)
        .toPage(pageable);
```

- Value fields (strings, numbers, booleans, dates, enums, UUIDs) are selected; other object and collection
  fields are skipped. Fields of a superclass are included when the superclass is a `@ForgeModel` too; fields
  of a shared plain base class are listed with `@ForgeModel(inherited = {"createdDate"})`.
- Values are written through setters, otherwise directly into the field. Records are created with their
  canonical constructor.
- Numbers convert to any number type, every value converts to `String`; anything else must be assignable.
- With `@ForgeModel(entity = Customer.class)` the annotation processor checks every path and type at
  compile time. Without it, the same checks run before the first query and throw a
  `QueryForgeMappingException` naming the field.

### With a lambda

`select` also takes a lambda over the model, for a single value or a computed result. Only the fields the lambda
reads are selected; the lambda then runs once per row on a model filled with those values:

```java
List<String> names = customerRepository.orderBy(c -> c.name).select(c -> c.name).distinct().toList();
List<CustomerRow> rows = customerRepository.select(c -> new CustomerRow(c.id, c.name + " (" + c.user.name + ")")).toList();
```

- Read fields only, not navigations themselves: `c.user.id == null` instead of `c.user == null`.
- `distinct()` adds sort keys that are not selected to the select list, as SQL requires.
- After `select`: `distinct`, `page`, `take`, `toList`, `toPage`, `firstOrDefault`, `count`, `any`.

## Aggregates

```java
BigDecimal total          = invoiceRepository.where(i -> i.customer.id.equals(id)).sum(i -> i.amount);   // 0 when no rows
Optional<LocalDate> last  = invoiceRepository.query().max(i -> i.dueDate);
Optional<BigDecimal> low  = invoiceRepository.query().min(i -> i.amount);
Optional<Double> average  = invoiceRepository.query().average(i -> i.amount);
```

## Notes

- Java has `>`/`<` only for primitive numbers, so `BigDecimal` and date fields are compared with
  `compareTo`, `isAfter` or `isBefore`.
- Navigations (`c.user.name`) are joined like EF Core does: a required association
  (`@ManyToOne(optional = false)`, `@OneToOne(optional = false)`) is an `INNER` join, any other one a `LEFT`
  join, and everything after an optional association stays `LEFT`. So `c.user.name.equals(x) || c.user == null`
  behaves as written for optional associations; mark an association `optional = false` only when its column
  is `NOT NULL` in the database. Comparisons follow SQL null semantics: `!c.active` does not match `NULL`.
- Anything else in a where or sort lambda (other method calls on fields, loops, nested lambdas) is rejected
  with a `QueryForgeUnsupportedExpressionException` naming the class and line, when the lambda is passed in.
- Captured variables are read when the lambda is passed in, like EF Core query parameters.

## Verifying lambdas at build time

Lambdas are translated when a query runs, so an unsupported expression would only fail then.
`QueryForgeVerifier` checks every where, sort and select lambda in compiled classes without a database
(captured values are treated as unknown):

```java
@Test
void allQueryLambdasTranslate() {
    assertEquals(List.of(), QueryForgeVerifier.verify(Path.of("target/classes"), getClass().getClassLoader()));
}
```

Or from the command line, with the application classes and their dependencies on the class path:
`java -cp <classpath> io.github.apertechprojects.queryforge.verify.QueryForgeVerifier target/classes`.

## Customization

- **EntityManager** — the shared `EntityManager` of the primary `EntityManagerFactory` is used. Declare an
  `EntityManagerProvider` bean to change it.

## License

[Apache License 2.0](LICENSE)
