package io.github.apertechprojects.queryforge.internal.rule.specification;

import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.internal.util.SpecificationUtil;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

public class SpecificationFilterGreaterThanRule implements ISpecificationFilterRule {
    @Override
    public Predicate generatePredicate(Path<?> root, CriteriaBuilder builder, Criteria criteria) {
        Predicate predicate;
        SpecificationUtil.checkHasFirstValue(criteria);
        predicate = builder.greaterThan(root.<Comparable>get(criteria.getKey()), (Comparable) criteria.getValues().get(0));

        return predicate;
    }
}
