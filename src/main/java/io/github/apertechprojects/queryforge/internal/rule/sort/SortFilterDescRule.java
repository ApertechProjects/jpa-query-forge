package io.github.apertechprojects.queryforge.internal.rule.sort;

import org.springframework.data.domain.Sort;

public class SortFilterDescRule implements ISortFilterRule {

    @Override
    public Sort getSortFilterRule(String field) {
        return Sort.by(Sort.Direction.DESC, field);
    }
}
