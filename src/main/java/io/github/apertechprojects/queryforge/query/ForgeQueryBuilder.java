package io.github.apertechprojects.queryforge.query;

import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.query.builder.*;
import io.github.apertechprojects.queryforge.repository.ForgeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.util.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ForgeQueryBuilder<T, ID> implements DistinctWhereOrderByPage<T, ID>, WhereOrderByPage<T, ID>, OrderByPage<T, ID>, PageableResult<T, ID>, Result<T, ID> {
    protected final ForgeRepository<T, ID> repository;
    protected final ForgeQuery forgeQuery;

    public ForgeQueryBuilder(ForgeRepository<T, ID> repository) {
        this.repository = repository;
        forgeQuery = new ForgeQuery();
    }

    public DistinctWhereOrderByPage<T, ID> select(Forge.SelectRule... selectRules) {
        forgeQuery.getSelect().addAll(Arrays.stream(selectRules).map(o -> Pair.of(o.getFieldName(), o.getAlias())).collect(Collectors.toList()));
        return this;
    }

    public WhereOrderByPage<T, ID> distinct(boolean distinct) {
        forgeQuery.setDistinct(distinct);
        return this;
    }


    public OrderByPage<T, ID> where(Criteria... criteria) {
        forgeQuery.getWhere().addAll(Arrays.asList(criteria));
        return this;
    }


    public PageableResult<T, ID> orderBy(Forge.OrderByRule... pairs) {
        forgeQuery.getOrderBy().addAll(Arrays.stream(pairs).map(o -> Pair.of(o.getFieldName(), o.getOrderType())).collect(Collectors.toList()));
        return this;
    }

    public Result<T, ID> page(int pageNumber, int pageSize) {
        forgeQuery.setPageSize(pageSize);
        forgeQuery.setPageNumber(pageNumber);
        return this;
    }

    public List<T> getResult() {
        return repository.findAll(forgeQuery);
    }

    public <ResultValue> List<ResultValue> getResult(Class<ResultValue> resultValueClass) {
        return repository.findAll(forgeQuery, resultValueClass);
    }

    public Page<T> getResultAsPage() {
        return repository.findAllAsPage(forgeQuery);
    }

    public <ResultValue> Page<ResultValue> getResultAsPage(Class<ResultValue> resultValueClass) {
        return repository.findAllAsPage(forgeQuery, resultValueClass);
    }
}
