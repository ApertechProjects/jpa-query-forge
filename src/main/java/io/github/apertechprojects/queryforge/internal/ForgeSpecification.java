package io.github.apertechprojects.queryforge.internal;

import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.criteria.CriteriaOperator;
import io.github.apertechprojects.queryforge.criteria.JoinType;
import io.github.apertechprojects.queryforge.exception.*;
import io.github.apertechprojects.queryforge.internal.util.SpecificationUtil;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.util.Pair;

import jakarta.persistence.criteria.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ForgeSpecification<Entity> implements Specification<Entity> {

    protected List<Criteria> criteriaList;
    /**
     * Joins created per {@link From}. Keyed by identity: Hibernate 7 SQM nodes implement structural
     * {@code equals}, so roots of different queries (e.g. a page query and its count query) compare equal.
     */
    protected Map<From<?, ?>, Map<Pair<String, JoinType>, Join<?, ?>>> joinMap;
    protected RepositoryContext context;

    public ForgeSpecification(List<Criteria> criteriaList, RepositoryContext context) {
        this.criteriaList = criteriaList;
        this.joinMap = Collections.synchronizedMap(new IdentityHashMap<>());
        this.context = context;
    }

    public ForgeSpecification(List<Criteria> criteriaList, Map<From<?, ?>, Map<Pair<String, JoinType>, Join<?, ?>>> joinMap, RepositoryContext context) {
        this.criteriaList = criteriaList;
        this.joinMap = joinMap;
        this.context = context;
    }

    @Override
    public Predicate toPredicate(Root<Entity> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
        List<Predicate> predicateAndList = new ArrayList<>();
        List<Predicate> predicateOrList = new ArrayList<>();
        for (int i = 0; i < criteriaList.size(); i++) {
            if (criteriaList.get(i).getOperation() == CriteriaOperator.PARENTHES) {
                SpecificationUtil.checkHasFirstValue(criteriaList.get(i));
                try {
                    predicateAndList.add(new ForgeSpecification<Entity>(((List<Criteria>) (criteriaList.get(i).getValues().get(0))), joinMap, context).toPredicate(root, query, builder));
                } catch (Exception e) {
                    throw new QueryForgeNoAvailableParenthesesOperationUsageException(
                            "There is No Available Parentheses Operation Usage in Criteria Key: " + criteriaList.get(i).getKey());
                }
            } else if (criteriaList.get(i).getOperation() == CriteriaOperator.OR) {
                if (i == 0 || i + 1 == criteriaList.size()) {
                    throw new QueryForgeNoAvailableOrOperationUsageException(
                            "There is No Available OR Operation Usage in Criteria Key: " + criteriaList.get(i).getKey());
                }

                predicateOrList.add(builder.and(predicateAndList.toArray(new Predicate[0])));
                predicateAndList.clear();
            } else {
                predicateAndList.add(getPredicate(root, builder, criteriaList.get(i)));
            }
        }

        predicateOrList.add(builder.and(predicateAndList.toArray(new Predicate[0])));

        return builder.or(predicateOrList.toArray(new Predicate[0]));
    }

    private Predicate getPredicate(Root<Entity> root, CriteriaBuilder builder, Criteria criteria) {
        From<?, ?> localFrom = createLocalFrom(root, criteria.getKey());
        return addPredicate(localFrom, builder, new Criteria(getFieldName(criteria.getKey()), criteria.getOperation(), criteria.getValues().toArray(new Object[0])));
    }

    public From<?, ?> createLocalFrom(Root<?> root, String key) {
        From<?, ?> localFrom = root;
        List<Pair<String, JoinType>> fieldJoins = getFieldJoins(key);

        for (Pair<String, JoinType> fieldJoin : fieldJoins) {
            localFrom = getJoin(localFrom, fieldJoin.getFirst(), fieldJoin.getSecond());
        }

        return localFrom;
    }

    public static List<Pair<String, JoinType>> getFieldJoins(String key) {
        List<Pair<String, JoinType>> fieldJoins = new ArrayList<>();

        String subKey = key;
        JoinType joinType = null;
        int index = -1;


        while (subKey.chars().anyMatch(c -> Arrays.stream(JoinType.values()).anyMatch(j -> j.getSeparator().charValue() == c))) {

            for (JoinType value : JoinType.values()) {
                int indexOf = subKey.indexOf(value.getSeparator());
                if (indexOf > -1 && (index == -1 || indexOf < index)) {
                    index = indexOf;
                    joinType = value;
                }
            }

            if (Objects.nonNull(joinType)) {
                fieldJoins.add(Pair.of(subKey.substring(0, index), joinType));
                subKey = subKey.substring(index + 1);
            }

            index = -1;
            joinType = null;
        }

        return fieldJoins;
    }

    public static String getFieldName(String key) {
        String[] splitedKey = key.split(">|<|\\.");
        return splitedKey[splitedKey.length - 1];
    }

    protected Predicate addPredicate(Path<?> root, CriteriaBuilder builder, Criteria criteria) {
        if (!criteria.getOperation().equals(CriteriaOperator.SPECIFIED)) {
            try {
                criteria.setValues(deserialize(root.get(criteria.getKey()).getJavaType(), criteria.getValues()));
            } catch (Exception e) {
                throw new QueryForgeNoAvailableEnumException("There is a "
                        + root.get(criteria.getKey()).getJavaType().getSimpleName() + "  Problem in Criteria Key: "
                        + criteria.getKey(), e);
            }
        }

        if (ForgeQueryExecutor.specificationRuleMap.containsKey(criteria.getOperation())) {
            return ForgeQueryExecutor.specificationRuleMap
                    .get(criteria.getOperation()).generatePredicate(root, builder, criteria);
        } else {
            throw new QueryForgeNoAvailableOperationException("There is No Available Operation in Criteria Key: "
                    + criteria.getKey());
        }
    }

    protected Join<?, ?> getJoin(From<?, ?> from, String key, JoinType joinType) {
        return joinMap.computeIfAbsent(from, f -> new ConcurrentHashMap<>())
                .computeIfAbsent(Pair.of(key, joinType), k -> from.join(key, joinType.getJoinType()));
    }

    protected List<Object> deserialize(Class<?> clazz, List<Object> objects) throws Exception {
        List<Object> result = new ArrayList<>();
        for (Object object : objects) {
            Object deserialized = null;
            try {
                deserialized = context.getDeserializer().deserialize(object.toString(), clazz);
            } catch (Exception e) {
                throw new QueryForgeValueSerializeException("There is a "
                        + clazz.getSimpleName() + " Deserialization Problem in Criteria Value: "
                        + object.toString());
            }
            result.add(deserialized);
        }
        return result;
    }
}
