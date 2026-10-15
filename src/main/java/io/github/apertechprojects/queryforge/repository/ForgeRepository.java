package io.github.apertechprojects.queryforge.repository;

import io.github.apertechprojects.queryforge.context.QueryForgeContext;
import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.internal.ForgeQueryExecutor;
import io.github.apertechprojects.queryforge.query.ForgeQuery;
import io.github.apertechprojects.queryforge.query.ForgeQueryBuilder;
import jakarta.persistence.Tuple;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * Spring Data JPA repository with dynamic criteria, projection and query-builder support.
 * <p>
 * All operations are default methods, so no custom repository base class or factory bean is required.
 */
@NoRepositoryBean
public interface ForgeRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {

    default List<T> findAll(List<Criteria> criteriaList) {
        return ForgeQueryExecutor.findAll(this, criteriaList, QueryForgeContext.repositoryContext());
    }

    default List<T> findAll(ForgeQuery forgeQuery) {
        return ForgeQueryExecutor.getEntityListBySelectableFilterAsList(this, forgeQuery, QueryForgeContext.repositoryContext());
    }

    default Page<T> findAllAsPage(ForgeQuery forgeQuery) {
        return ForgeQueryExecutor.getEntityListBySelectableFilterAsPage(this, forgeQuery, QueryForgeContext.repositoryContext());
    }

    default List<Tuple> findAllAsTuple(ForgeQuery forgeQuery) {
        return ForgeQueryExecutor.getEntityListBySelectableFilterWithTupleAsList(this, forgeQuery, QueryForgeContext.repositoryContext());
    }

    default Page<Tuple> findAllAsTuplePage(ForgeQuery forgeQuery) {
        return ForgeQueryExecutor.getEntityListBySelectableFilterWithTupleAsPage(this, forgeQuery, QueryForgeContext.repositoryContext());
    }

    default <ResultType> List<ResultType> findAll(ForgeQuery forgeQuery, Class<ResultType> resultTypeClass) {
        return ForgeQueryExecutor.getEntityListBySelectableFilterWithReturnTypeAsList(this, forgeQuery, resultTypeClass, QueryForgeContext.repositoryContext());
    }

    default <ResultType> Page<ResultType> findAllAsPage(ForgeQuery forgeQuery, Class<ResultType> resultTypeClass) {
        return ForgeQueryExecutor.getEntityListBySelectableFilterWithReturnTypeAsPage(this, forgeQuery, resultTypeClass, QueryForgeContext.repositoryContext());
    }

    default ForgeQueryBuilder<T, ID> queryBuilder() {
        return new ForgeQueryBuilder<>(this);
    }

    default Page<T> findAll(List<Criteria> criteriaList, Pageable pageable) {
        return ForgeQueryExecutor.findAll(this, criteriaList, pageable, QueryForgeContext.repositoryContext());
    }

    static <T> Specification<T> createSpecification(List<Criteria> criteriaList) {
        return ForgeQueryExecutor.getSpecification(criteriaList, QueryForgeContext.repositoryContext());
    }

    default long count(List<Criteria> criteriaList) {
        return ForgeQueryExecutor.count(this, criteriaList, QueryForgeContext.repositoryContext());
    }

    default void consumePartially(ListConsumer<T> processor, int pageSize) {
        consumePartially((Specification<T>) null, processor, pageSize);
    }

    default void consumePartially(Specification<T> specification, ListConsumer<T> processor, int pageSize) {
        Page<T> page = this.findAll(specification, PageRequest.of(0, pageSize));
        processor.accept(page.getContent());
        long totalElements = page.getTotalElements();
        for (int i = 1; (long) i * pageSize < totalElements; i++) {
            page = this.findAll(specification, PageRequest.of(i, pageSize));
            processor.accept(page.getContent());
        }
    }

    default void consumePartially(List<Criteria> criteriaList, ListConsumer<T> processor, int pageSize) {
        long totalElements = count(criteriaList);

        for (int i = 0; (long) i * pageSize < totalElements; i++) {
            processor.accept(findAll(criteriaList, PageRequest.of(i, pageSize)).getContent());
        }
    }
}
