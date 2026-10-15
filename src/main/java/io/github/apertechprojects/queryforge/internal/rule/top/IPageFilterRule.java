package io.github.apertechprojects.queryforge.internal.rule.top;

import org.springframework.data.domain.Pageable;

public interface IPageFilterRule {
    Pageable generatePageRequest(int page, int size, String field);
}
