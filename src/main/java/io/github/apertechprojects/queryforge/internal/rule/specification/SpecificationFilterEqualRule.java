package io.github.apertechprojects.queryforge.internal.rule.specification;

import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.internal.util.SpecificationUtil;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

public class SpecificationFilterEqualRule implements ISpecificationFilterRule {

    @Override
    public Predicate generatePredicate(Path<?> root, CriteriaBuilder builder, Criteria criteria) {
        SpecificationUtil.checkHasFirstValue(criteria);
        Predicate[] predicates = new Predicate[criteria.getValues().size()];
        for (int i = 0; i < criteria.getValues().size(); i++) {
            predicates[i] = builder.equal(root.get(criteria.getKey()), criteria.getValues().get(i));
        }

        return builder.or(predicates);
    }
}
