package io.github.apertechprojects.queryforge.internal.rule.top;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageFilterDescRule implements IPageFilterRule {

    @Override
    public Pageable generatePageRequest(int page, int size, String field) {
        return PageRequest.of(page, size, Sort.Direction.DESC, field);
    }
}
