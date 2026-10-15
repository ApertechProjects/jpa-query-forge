# Changelog

## 1.0.0 (2026-10-15)

Initial release, derived from spring-data-dynamic-query (`sb3-v0.4.0` / `0.8.0` JPA behaviour).

- Packages moved to `io.github.apertechprojects.queryforge`; branded types renamed.
- Spring Boot 3.0–4.1 support from a single artifact; standard JPA APIs only (no Hibernate internals).
- Repository operations are interface default methods: no repository base class, factory bean or
  `@EnableJpaRepositories` override required. Spring Boot auto-configuration included.
- Custom `ValueDeserializer` / `EntityManagerProvider` beans are resolved lazily, independent of
  configuration order.
- Fixed: `consumePartially(Specification, ...)` ignored the specification on the first page.
- Fixed: join cache collisions on Hibernate 7 ("Already registered a copy").
- Fixed: default DTO projection selected associations (implicit inner join) and missed inherited attributes.
- Removed: `JacksonDeserializer` (no hard Jackson dependency), Lombok, commons-collections4.
