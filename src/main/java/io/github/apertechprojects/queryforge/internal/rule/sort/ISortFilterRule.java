package io.github.apertechprojects.queryforge.internal.rule.sort;

import org.springframework.data.domain.Sort;

public interface ISortFilterRule {
    Sort getSortFilterRule(String field);
}
