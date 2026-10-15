package io.github.apertechprojects.queryforge.internal;

import io.github.apertechprojects.queryforge.annotation.ForgeModel;
import io.github.apertechprojects.queryforge.annotation.ForgeField;
import io.github.apertechprojects.queryforge.annotation.ForgeIgnore;
import io.github.apertechprojects.queryforge.annotation.ForgeSubModel;
import io.github.apertechprojects.queryforge.criteria.Criteria;
import io.github.apertechprojects.queryforge.query.ForgeQuery;
import io.github.apertechprojects.queryforge.criteria.CriteriaOperator;
import io.github.apertechprojects.queryforge.exception.QueryForgeIllegalArgumentException;
import io.github.apertechprojects.queryforge.internal.rule.specification.*;
import io.github.apertechprojects.queryforge.internal.field.FieldUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.util.Pair;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import jakarta.persistence.TupleElement;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ForgeQueryExecutor {

    public final static Map<CriteriaOperator, ISpecificationFilterRule> specificationRuleMap = new HashMap<>();

    static {
        specificationRuleMap.put(CriteriaOperator.CONTAIN, new SpecificationFilterDoubleLikeRule());
        specificationRuleMap.put(CriteriaOperator.DOES_NOT_CONTAIN, new SpecificationFilterDoesNotContainRule());
        specificationRuleMap.put(CriteriaOperator.END_WITH, new SpecificationFilterLeftLikeRule());
        specificationRuleMap.put(CriteriaOperator.START_WITH, new SpecificationFilterRightLikeRule());
        specificationRuleMap.put(CriteriaOperator.SPECIFIED, new SpecificationFilterSpecifiedRule());
        specificationRuleMap.put(CriteriaOperator.EQUAL, new SpecificationFilterEqualRule());
        specificationRuleMap.put(CriteriaOperator.NOT_EQUAL, new SpecificationFilterNotEqualRule());
        specificationRuleMap.put(CriteriaOperator.GREATER_THAN, new SpecificationFilterGreaterThanRule());
        specificationRuleMap.put(CriteriaOperator.GREATER_THAN_OR_EQUAL, new SpecificationFilterGreaterThanOrEqualToRule());
        specificationRuleMap.put(CriteriaOperator.LESS_THAN, new SpecificationFilterLessThanRule());
        specificationRuleMap.put(CriteriaOperator.LESS_THAN_OR_EQUAL, new SpecificationFilterLessThanOrEqualToRule());
        specificationRuleMap.put(CriteriaOperator.OR, null);
        specificationRuleMap.put(CriteriaOperator.PARENTHES, null);
    }

    public static <Entity> List<Entity> findAll(JpaSpecificationExecutor<Entity> repositoryExecutor,
                                                List<Criteria> searchCriteriaList, RepositoryContext context) {
        List<Entity> result;
        List<Criteria> specificationRules = getMapSpecificRules(specificationRuleMap, searchCriteriaList);
        ForgeSpecification<Entity> specification = null;

        if (!specificationRules.isEmpty()) {
            specification = new ForgeSpecification<>(specificationRules, context);
        }

        return repositoryExecutor.findAll(specification);
    }

    public static <Entity> Page<Entity> findAll(JpaSpecificationExecutor<Entity> repositoryExecutor,
                                                List<Criteria> searchCriteriaList, Pageable pageable, RepositoryContext context) {
        List<Criteria> specificationRules = getMapSpecificRules(specificationRuleMap, searchCriteriaList);

        ForgeSpecification<Entity> specification = null;
        if (!specificationRules.isEmpty()) {
            specification = new ForgeSpecification<>(specificationRules, context);
        }

        return repositoryExecutor.findAll(specification, pageable);
    }

    public static <Entity> Specification<Entity> getSpecification(List<Criteria> searchCriteriaList, RepositoryContext context) {
        List<Criteria> specificationRules = getMapSpecificRules(specificationRuleMap, searchCriteriaList);

        ForgeSpecification<Entity> specification = null;
        if (!specificationRules.isEmpty()) {
            specification = new ForgeSpecification<>(specificationRules, context);
        }

        return specification;
    }

    public static <Entity> long count(JpaSpecificationExecutor<Entity> repositoryExecutor,
                                      List<Criteria> searchCriteriaList, RepositoryContext context) {
        Long result = 0l;
        List<Criteria> specificationRules = getMapSpecificRules(specificationRuleMap, searchCriteriaList);
        ForgeSpecification<Entity> specification = null;
        if (specificationRules == null || !specificationRules.isEmpty()) {
            specification = new ForgeSpecification<>(specificationRules, context);
        }
        result = repositoryExecutor.count(specification);

        return result;
    }

    public static <Entity> List<Entity> getEntityListBySelectableFilterAsList(JpaSpecificationExecutor<Entity> repositoryExecutor, ForgeQuery forgeQuery, RepositoryContext context) {
        return (List<Entity>) ForgeQueryExecutor.getEntityListBySelectableFilter(repositoryExecutor, forgeQuery, false, context);
    }

    public static <Entity> Page<Entity> getEntityListBySelectableFilterAsPage(JpaSpecificationExecutor<Entity> repositoryExecutor, ForgeQuery forgeQuery, RepositoryContext context) {
        return (Page<Entity>) ForgeQueryExecutor.getEntityListBySelectableFilter(repositoryExecutor, forgeQuery, true, context);
    }

    protected static <Entity> Iterable<Entity> getEntityListBySelectableFilter(JpaSpecificationExecutor<Entity> repositoryExecutor, ForgeQuery forgeQuery, boolean isPage, RepositoryContext context) {
        Class<Entity> entityClass = getEntityClass(repositoryExecutor);
        return getEntityListBySelectableFilterWithReturnType(repositoryExecutor, forgeQuery, entityClass, isPage, context);
    }

    public static <Entity> List<Tuple> getEntityListBySelectableFilterWithTupleAsList(JpaSpecificationExecutor<Entity> repositoryExecutor, ForgeQuery forgeQuery, RepositoryContext context) {
        return (List<Tuple>) ForgeQueryExecutor.getEntityListBySelectableFilterWithTuple(repositoryExecutor, forgeQuery, false, context);
    }

    public static <Entity> Page<Tuple> getEntityListBySelectableFilterWithTupleAsPage(JpaSpecificationExecutor<Entity> repositoryExecutor, ForgeQuery forgeQuery, RepositoryContext context) {
        return (Page<Tuple>) ForgeQueryExecutor.getEntityListBySelectableFilterWithTuple(repositoryExecutor, forgeQuery, true, context);
    }

    protected static <Entity> Iterable<Tuple> getEntityListBySelectableFilterWithTuple(JpaSpecificationExecutor<Entity> repositoryExecutor, ForgeQuery forgeQuery, boolean isPage, RepositoryContext context) {
        return getEntityListWithReturnClass(repositoryExecutor, forgeQuery, Tuple.class, isPage, context);
    }

    public static <Entity, ResultType> List<ResultType> getEntityListBySelectableFilterWithReturnTypeAsList(JpaSpecificationExecutor<Entity> repositoryExecutor, ForgeQuery forgeQuery, Class<ResultType> resultTypeClass, RepositoryContext context) {
        return (List<ResultType>) ForgeQueryExecutor.getEntityListBySelectableFilterWithReturnType(repositoryExecutor, forgeQuery, resultTypeClass, false, context);
    }

    public static <Entity, ResultType> Page<ResultType> getEntityListBySelectableFilterWithReturnTypeAsPage(JpaSpecificationExecutor<Entity> repositoryExecutor, ForgeQuery forgeQuery, Class<ResultType> resultTypeClass, RepositoryContext context) {
        return (Page<ResultType>) ForgeQueryExecutor.getEntityListBySelectableFilterWithReturnType(repositoryExecutor, forgeQuery, resultTypeClass, true, context);
    }

    protected static <Entity, ResultType> Iterable<ResultType> getEntityListBySelectableFilterWithReturnType(JpaSpecificationExecutor<Entity> repositoryExecutor, ForgeQuery forgeQuery, Class<ResultType> resultTypeClass, boolean isPage, RepositoryContext context) {
        Class<Entity> entityClass = getEntityClass(repositoryExecutor);
        if (resultTypeClass.equals(entityClass) && CollectionUtils.isEmpty(forgeQuery.getSelect())) {
            return getEntityListWithReturnClass(repositoryExecutor, forgeQuery, resultTypeClass, isPage, context);
        } else {
            extractIfForgeModel(forgeQuery, resultTypeClass);
            Iterable<Tuple> entityListBySelectableFilter = getEntityListWithReturnClass(repositoryExecutor, forgeQuery, Tuple.class, isPage, context);

            if (!CollectionUtils.isEmpty(forgeQuery.getSelect())) {
                return convertResultToResultTypeList(forgeQuery.getSelect(), resultTypeClass, entityListBySelectableFilter, isPage);
            } else {

                Iterator<Tuple> iterator = entityListBySelectableFilter.iterator();
                if (iterator.hasNext()) {
                    // default selections are aliased with their attribute name (see getEntityListWithReturnClass)
                    List<Pair<String, String>> parameters = iterator.next().getElements().stream()
                            .map(TupleElement::getAlias)
                            .filter(Objects::nonNull)
                            .map(alias -> Pair.of(alias, alias))
                            .collect(Collectors.toList());
                    return convertResultToResultTypeList(parameters, resultTypeClass, entityListBySelectableFilter, isPage);
                } else {
                    return new ArrayList<>();
                }
            }
        }
    }

    private static <ResultType> void extractIfForgeModel(ForgeQuery forgeQuery, Class<ResultType> resultTypeClass) {
        if (!resultTypeClass.isAnnotationPresent(ForgeModel.class)) {
            return;
        }

        List<Pair<String, String>> select = new ArrayList<>();
        recursiveSupModelFiller(resultTypeClass, select, new ArrayList<>(), "");
        forgeQuery.setSelect(select);
    }

    private static <ResultType> void recursiveSupModelFiller(Class<ResultType> resultTypeClass, List<Pair<String, String>> select, List<String> dbPrefixList, String entityPrefix) {
        for (Field declaredField : resultTypeClass.getDeclaredFields()) {
            if (declaredField.isAnnotationPresent(ForgeSubModel.class)) {
                String subModelValue = declaredField.getAnnotation(ForgeSubModel.class).value();
                ArrayList<String> newPrefixList = new ArrayList<>(dbPrefixList);
                if (StringUtils.isNotBlank(subModelValue)) {
                    newPrefixList.add(subModelValue);
                }
                recursiveSupModelFiller(declaredField.getType(), select, newPrefixList, entityPrefix + declaredField.getName() + ".");
            } else if (FieldUtil.isSupportedType(declaredField.getType())) {
                if (declaredField.isAnnotationPresent(ForgeIgnore.class)) {
                    if (resultTypeClass.isRecord()) {
                        throw new QueryForgeIllegalArgumentException("Record class can not have @ForgeIgnore annotation");
                    }
                    continue;
                }

                if (declaredField.isAnnotationPresent(ForgeField.class)) {
                    select.add(Pair.of(prefixCreator(dbPrefixList) + declaredField.getAnnotation(ForgeField.class).value(), entityPrefix + declaredField.getName()));
                } else {
                    select.add(Pair.of(prefixCreator(dbPrefixList) + declaredField.getName(), entityPrefix + declaredField.getName()));
                }
            } else {
                if (resultTypeClass.isRecord()) {
                    throw new QueryForgeIllegalArgumentException("Record didnt support nested model type: " + declaredField.getType().getName());
                }
            }
        }
    }

    private static String prefixCreator(List<String> prefixList) {
        String collect = String.join(".", prefixList);
        if (StringUtils.isNotBlank(collect)) {
            collect += ".";
        }
        return collect;
    }

    protected static <Entity, ResultType> Iterable<ResultType> getEntityListWithReturnClass(JpaSpecificationExecutor<Entity> repositoryExecutor, ForgeQuery forgeQuery, Class<ResultType> resultTypeClass, boolean isPage, RepositoryContext context) {
        Class<Entity> entityClass = getEntityClass(repositoryExecutor);
        EntityManager entityManager = context.getEntityManager();
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<ResultType> query = builder.createQuery(resultTypeClass);
        Root<Entity> root = query.from(entityClass);
        ForgeSpecification<Entity> specification = new ForgeSpecification<>(forgeQuery.getWhere(), context);
        Pageable pageable = Pageable.unpaged();

        query.distinct(forgeQuery.isDistinct());

        if (!CollectionUtils.isEmpty(forgeQuery.getSelect())) {
            List<Selection<?>> selectionList = new ArrayList<>();

            forgeQuery.getSelect().forEach(selectField -> {
                selectionList.add(specification.createLocalFrom(root, selectField.getFirst()).get(ForgeSpecification.getFieldName(selectField.getFirst())).alias(selectField.getSecond()));
            });
            query.multiselect(selectionList);
        } else if (!resultTypeClass.equals(entityClass)) {
            List<Selection<?>> selectionList = new ArrayList<>();
            // only basic/embedded attributes: selecting an association would add an implicit inner join
            for (SingularAttribute<? super Entity, ?> attribute : root.getModel().getSingularAttributes()) {
                Attribute.PersistentAttributeType type = attribute.getPersistentAttributeType();
                if (type == Attribute.PersistentAttributeType.BASIC || type == Attribute.PersistentAttributeType.EMBEDDED) {
                    selectionList.add(root.get(attribute).alias(attribute.getName()));
                }
            }
            query.multiselect(selectionList);
        }

        if (!CollectionUtils.isEmpty(forgeQuery.getWhere())) {
            query.where(specification.toPredicate(root, query, builder));
        }

        if (!CollectionUtils.isEmpty(forgeQuery.getOrderBy())) {
            List<Order> orderList = new ArrayList<>();
            forgeQuery.getOrderBy().forEach((orderPair) -> {
                orderList.add(orderPair.getSecond() == io.github.apertechprojects.queryforge.criteria.Order.DESC ? builder.desc(specification.createLocalFrom(root, orderPair.getFirst()).get(ForgeSpecification.getFieldName(orderPair.getFirst()))) : builder.asc(specification.createLocalFrom(root, orderPair.getFirst()).get(ForgeSpecification.getFieldName(orderPair.getFirst()))));
            });
            query.orderBy(orderList);
        }

        TypedQuery<ResultType> typedQuery = entityManager.createQuery(query);


        if (Objects.nonNull(forgeQuery.getPageSize())) {
            typedQuery.setMaxResults(forgeQuery.getPageSize());

            if (Objects.nonNull(forgeQuery.getPageNumber())) {
                typedQuery.setFirstResult(forgeQuery.getPageSize() * forgeQuery.getPageNumber());

                if (!CollectionUtils.isEmpty(forgeQuery.getOrderBy())) {
                    pageable = PageRequest.of(forgeQuery.getPageNumber(), forgeQuery.getPageSize(), forgeQuery.getOrderBy().get(0).getSecond().getDirection(), forgeQuery.getOrderBy().stream().map(Pair::getFirst).toArray(String[]::new));
                } else {
                    pageable = PageRequest.of(forgeQuery.getPageNumber(), forgeQuery.getPageSize());
                }
            }
        }

        if (isPage) {
            return PageableExecutionUtils.getPage(typedQuery.getResultList(), pageable,
                    () -> executeCountQuery(getCountQuery(specification, entityClass, context)));
        } else {
            return typedQuery.getResultList();
        }
    }


    protected static <S> TypedQuery<Long> getCountQuery(@Nullable Specification<S> spec, Class<S> domainClass, RepositoryContext context) {
        EntityManager entityManager = context.getEntityManager();
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);

        Root<S> root = applySpecificationToCriteria(spec, domainClass, query, context);

        if (query.isDistinct()) {
            query.select(builder.countDistinct(root));
        } else {
            query.select(builder.count(root));
        }

        // Remove all Orders the Specifications might have applied
        query.orderBy(Collections.<Order>emptyList());

        return entityManager.createQuery(query);
    }

    protected static <S, U> Root<U> applySpecificationToCriteria(@Nullable Specification<U> spec, Class<U> domainClass,
                                                                 CriteriaQuery<S> query, RepositoryContext context) {

        Assert.notNull(domainClass, "Domain class must not be null!");
        Assert.notNull(query, "CriteriaQuery must not be null!");
        EntityManager entityManager = context.getEntityManager();
        Root<U> root = query.from(domainClass);

        if (spec == null) {
            return root;
        }

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        Predicate predicate = spec.toPredicate(root, query, builder);

        if (predicate != null) {
            query.where(predicate);
        }

        return root;
    }


    protected static long executeCountQuery(TypedQuery<Long> query) {

        Assert.notNull(query, "TypedQuery must not be null!");

        List<Long> totals = query.getResultList();
        long total = 0L;

        for (Long element : totals) {
            total += element == null ? 0 : element;
        }

        return total;
    }

    protected static <ResultType> Iterable<ResultType> convertResultToResultTypeList(List<Pair<String, String>> querySelects, Class<ResultType> resultTypeClass, Iterable<Tuple> entityListBySelectableFilter, boolean isPage) {
        Stream<Tuple> stream = isPage ? ((Page<Tuple>) entityListBySelectableFilter).stream() : ((List<Tuple>) entityListBySelectableFilter).stream();

        List<ResultType> resultTypeList;

        Map<String, Integer> selectsWithIndex = new HashMap<>();
        for (int i = 0; i < querySelects.size(); i++) {
            selectsWithIndex.put(querySelects.get(i).getSecond(), i);
        }

        Map<Class<?>, Map<Integer, Method>> classSetterMethodsMap = new HashMap<>();
        Map<Class<?>, Constructor<?>> recordConstructorMap = new HashMap<>();

        resultTypeList = stream.map(t -> fillModel(resultTypeClass, t, selectsWithIndex, classSetterMethodsMap, recordConstructorMap)).filter(Objects::nonNull).collect(Collectors.toList());


        if (isPage) {
            Page<Tuple> tuplePage = (Page<Tuple>) entityListBySelectableFilter;
            return new PageImpl<>(resultTypeList, tuplePage.getPageable(), tuplePage.getTotalElements());
        } else {
            return resultTypeList;
        }
    }

    protected static <ModelType> ModelType fillModel(Class<ModelType> modelType, Tuple t, Map<String, Integer> selectsWithIndex, Map<Class<?>, Map<Integer, Method>> classSetterMethodsMap, Map<Class<?>, Constructor<?>> recordConstructorMap) {
        Map<String, Object> subModelMap = new HashMap<>();
        for (Field declaredField : modelType.getDeclaredFields()) {
            if (declaredField.isAnnotationPresent(ForgeSubModel.class)) {
                subModelMap.put(declaredField.getName(), fillModel(declaredField.getType(), t, selectsWithIndex.entrySet().stream().filter(e -> e.getKey().startsWith(declaredField.getName() + "."))
                        .collect(Collectors.toMap(k -> k.getKey().substring(declaredField.getName().length() + 1), Map.Entry::getValue)), classSetterMethodsMap, recordConstructorMap));
            }
        }


        if (modelType.isRecord()) {
            try {
                Constructor<ModelType> constructor = (Constructor<ModelType>) recordConstructorMap.get(modelType);
                if (Objects.isNull(constructor)) {
                    constructor = modelType.getConstructor(Arrays.stream(modelType.getRecordComponents())
                            .map(RecordComponent::getType)
                            .toArray(Class[]::new));
                    recordConstructorMap.put(modelType, constructor);
                }

                // canonical constructor parameters follow record component order; component names are always available
                RecordComponent[] components = modelType.getRecordComponents();
                Object[] args = new Object[components.length];
                for (int i = 0; i < components.length; i++) {
                    String name = components[i].getName();
                    if (selectsWithIndex.containsKey(name)) {
                        args[i] = t.get(selectsWithIndex.get(name));
                    } else {
                        args[i] = subModelMap.get(name);
                    }
                }

                return constructor.newInstance(args);
            } catch (Exception e) {
                return null;
            }
        } else {
            List<Map.Entry<String, Integer>> fieldList = selectsWithIndex.entrySet().stream().filter(e -> !e.getKey().contains(".")).distinct().sorted(Comparator.comparing(Map.Entry::getValue)).toList();
            Map<Integer, Method> setterMethods = getIntegerMethodMap(fieldList.stream().map(e -> Pair.of(e.getValue(), e.getKey())).collect(Collectors.toList()), modelType, classSetterMethodsMap);
            try {
                ModelType resultObj = modelType.getConstructor().newInstance();
                for (Map.Entry<Integer, Method> entry : setterMethods.entrySet()) {
                    entry.getValue().invoke(resultObj, t.get(entry.getKey()));
                }
                for (Map.Entry<String, Object> stringObjectEntry : subModelMap.entrySet()) {
                    Field declaredField = resultObj.getClass().getDeclaredField(stringObjectEntry.getKey());
                    boolean canAccess = declaredField.canAccess(resultObj);
                    declaredField.setAccessible(true);
                    declaredField.set(resultObj, stringObjectEntry.getValue());
                    declaredField.setAccessible(canAccess);
                }
                return resultObj;
            } catch (Exception e) {
                return null;
            }
        }
    }


    private static <ResultType> Map<Integer, Method> getIntegerMethodMap(List<Pair<Integer, String>> querySelects, Class<ResultType> resultTypeClass, Map<Class<?>, Map<Integer, Method>> classSetterMethodsMap) {
        Map<Integer, Method> setterMethods = new HashMap<>();
        if (classSetterMethodsMap.containsKey(resultTypeClass)) {
            setterMethods = classSetterMethodsMap.get(resultTypeClass);
        } else {
            for (int i = 0; i < querySelects.size(); i++) {
                String select = querySelects.get(i).getSecond();

                Optional<Method> methodOptional = Arrays.stream(resultTypeClass.getMethods())
                        .filter(c -> c.getName().equalsIgnoreCase("set" + select)
                                && c.getParameterCount() == 1).findFirst();

                if (methodOptional.isPresent()) {
                    setterMethods.put(querySelects.get(i).getFirst(), methodOptional.get());
                }
            }
            classSetterMethodsMap.put(resultTypeClass, setterMethods);
        }
        return setterMethods;
    }

    @SuppressWarnings("unchecked")
    protected static <Entity> Class<Entity> getEntityClass(JpaSpecificationExecutor<Entity> repositoryExecutor) {
        Class<Entity> entityClass = (Class<Entity>) GenericTypeResolver.resolveTypeArgument(repositoryExecutor.getClass(), JpaSpecificationExecutor.class);
        if (Objects.nonNull(entityClass)) {
            return entityClass;
        }
        throw new IllegalStateException("Entity class could not be resolved for repository " + repositoryExecutor.getClass().getName());
    }

    protected static <M extends Map<CriteriaOperator, ?>> List<Criteria> getMapSpecificRules(M map, List<Criteria> searchCriteriaList) {
        List<Criteria> result = new ArrayList<>();

        for (Criteria criteria : searchCriteriaList) {
            if (map.containsKey(criteria.getOperation())) {
                result.add(criteria);
            }
        }

        return result;
    }
}
