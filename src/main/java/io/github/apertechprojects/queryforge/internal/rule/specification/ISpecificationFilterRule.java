package io.github.apertechprojects.queryforge.internal.rule.specification;


import io.github.apertechprojects.queryforge.criteria.Criteria;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

public interface ISpecificationFilterRule {
    Predicate generatePredicate(Path<?> root, CriteriaBuilder builder, Criteria criteria);
}
