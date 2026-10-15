package io.github.apertechprojects.queryforge.internal.rule.top;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class TopFilterAscRule implements IPageFilterRule {

    @Override
    public Pageable generatePageRequest(int page, int size, String field) {
        return PageRequest.of(0, size, Sort.Direction.ASC, field);
    }
}
