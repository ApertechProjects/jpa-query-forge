package io.github.apertechprojects.queryforge.internal.rule.specification;

import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.exception.QueryForgeNoAvailableValueException;
import io.github.apertechprojects.queryforge.internal.util.SpecificationUtil;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

public class SpecificationFilterDoubleLikeRule implements ISpecificationFilterRule {

    @Override
    public Predicate generatePredicate(Path<?> root, CriteriaBuilder builder, Criteria criteria) {
        SpecificationUtil.checkHasFirstValue(criteria);
        Predicate[] predicates = new Predicate[criteria.getValues().size()];
        for (int i = 0; i < criteria.getValues().size(); i++) {
            if (root.get(criteria.getKey()).getJavaType() == String.class) {
                predicates[i] = builder.like(root.<String>get(criteria.getKey()), "%" + criteria.getValues().get(i) + "%");
            } else {
                throw new QueryForgeNoAvailableValueException("Need String Type: " + criteria.getKey());
            }
        }

        return builder.or(predicates);
    }
}
