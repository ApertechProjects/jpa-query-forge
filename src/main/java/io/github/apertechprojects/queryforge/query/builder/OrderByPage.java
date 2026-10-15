package io.github.apertechprojects.queryforge.query.builder;

import io.github.apertechprojects.queryforge.query.Forge;
import org.springframework.data.domain.Page;

import java.util.List;

public interface OrderByPage<T, ID> {
    PageableResult<T, ID> orderBy(Forge.OrderByRule... pairs);

    Result<T, ID> page(int pageNumber, int pageSize);

    List<T> getResult();

    Page<T> getResultAsPage();

    <ResultValue> List<ResultValue> getResult(Class<ResultValue> resultValueClass);

    <ResultValue> Page<ResultValue> getResultAsPage(Class<ResultValue> resultValueClass);
}
