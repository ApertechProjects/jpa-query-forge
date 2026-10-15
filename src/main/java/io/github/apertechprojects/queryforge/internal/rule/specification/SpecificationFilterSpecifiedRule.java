package io.github.apertechprojects.queryforge.internal.rule.specification;

import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.exception.QueryForgeNoAvailableValueException;
import io.github.apertechprojects.queryforge.internal.util.SpecificationUtil;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

public class SpecificationFilterSpecifiedRule implements ISpecificationFilterRule {

    @Override
    public Predicate generatePredicate(Path<?> root, CriteriaBuilder builder, Criteria criteria) {
        SpecificationUtil.checkHasFirstValue(criteria);
        if (!criteria.getValues().get(0).toString().equalsIgnoreCase("true") && !criteria.getValues().get(0).toString().equalsIgnoreCase("false")) {
            throw new QueryForgeNoAvailableValueException("Specified rule first value must be true or false. But you send " + criteria.getValues().get(0).toString());
        }

        return criteria.getValues().get(0).toString().equalsIgnoreCase("true") ? builder.isNotNull(root.get(criteria.getKey())) : builder.isNull(root.get(criteria.getKey()));
    }
}
