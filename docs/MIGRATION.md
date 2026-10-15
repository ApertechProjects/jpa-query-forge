# Migrating from spring-(data-)jpa-dynamic-query

JPA Query Forge keeps the behaviour and method signatures of the original library; only packages and a
few type names changed. Both the single-module layout (`spring-jpa-dynamic-query` ≤ 0.4.x,
`com.beyt.jdq.*`) and the multi-module layout (`spring-data-jpa-dynamic-query` 0.5+,
`com.beyt.jdq.core.*` / `com.beyt.jdq.jpa.*`) are supported.

## Automatic migration

```bash
scripts/migrate-from-jdq.sh /path/to/project            # uses 0.1.0-SNAPSHOT
scripts/migrate-from-jdq.sh /path/to/project 0.1.0      # explicit version
```

The script
- rewrites imports and renamed types in every `*.java` file that references `com.beyt.jdq`
  (other files are left untouched),
- replaces the dependency in `pom.xml`,
- exits with code 2 and lists the lines if any reference to the old library remains.

Commit (or back up) before running it, then review the diff and build.

## Name mapping

| Old | New |
|---|---|
| `JpaDynamicQueryRepository` | `io.github.apertechprojects.queryforge.repository.ForgeRepository` |
| `@EnableJpaDynamicQuery` | `io.github.apertechprojects.queryforge.annotation.EnableQueryForge` |
| `@EnableJpaDynamicQueryArgumentResolvers`, `@EnableDynamicQueryArgumentResolvers` | `...annotation.EnableQueryForgeWebArgumentResolvers` |
| `QuerySimplifier` (`Select`, `OrderBy`, `Field`, `OR`, `Parantesis`) | `...query.Forge` (`Select`, `OrderBy`, `Field`, `OR`, `Parenthesis`) |
| `QueryBuilder` | `...query.ForgeQueryBuilder` |
| `DynamicQuery` | `...query.ForgeQuery` |
| `@JdqModel`, `@JdqField`, `@JdqIgnoreField`, `@JdqSubModel` | `@ForgeModel`, `@ForgeField`, `@ForgeIgnore`, `@ForgeSubModel` (`...annotation`) |
| `IDeserializer`, `BasicDeserializer` | `...deserializer.ValueDeserializer`, `DefaultValueDeserializer` |
| `IEntityManagerProvider` | `...context.EntityManagerProvider` |
| `DynamicQuery*Exception` | `...exception.QueryForge*Exception` (all extend `QueryForgeException`) |
| `Criteria`, `CriteriaList`, `CriteriaOperator`, `Order`, `JoinType` | same names in `...criteria` |
| `PageableResult`, `DistinctWhereOrderByPage`, `OrderByPage`, `WhereOrderByPage`, `Result` | same names in `...query.builder` |
| `ListConsumer` | `...repository.ListConsumer` |

## Behaviour differences

- `@EnableQueryForge` does not declare `@EnableJpaRepositories` (0.5+ did, which restricted repository
  scanning to the annotated class's package). With Spring Boot the annotation is optional.
- `consumePartially(Specification, ...)` now applies the specification to the first page as well.
- Projecting into a non-entity class without an explicit `select` maps only basic and embedded
  attributes (inherited ones included). Associations are no longer selected, so no implicit inner join
  drops rows with a `null` foreign key.
- Join caching is identity-based, which fixes "Already registered a copy: SqmSingularJoin" errors on
  Hibernate 7 (Spring Boot 4) for paged queries over joined paths.
- `update(Object)` (0.8.0) and the MongoDB/Elasticsearch modules are not part of this library yet.
