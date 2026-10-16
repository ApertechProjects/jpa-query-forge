package io.github.apertechprojects.queryforge.internal.expression;

import io.github.apertechprojects.queryforge.exception.QueryForgeUnsupportedExpressionException;
import io.github.apertechprojects.queryforge.expression.ForgeSelector;
import io.github.apertechprojects.queryforge.internal.Projection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Select lambda ({@code o -> new Row(o.id, o.supplier.name)}) split into the attribute paths it reads and a
 * mapper that fills a query model with one result row and calls the lambda on it.
 *
 * @param <R> result type of the lambda
 */
public final class ModelProjection<R> implements Projection<R> {

    private final ForgeSelector<Object, R> selector;
    private final Class<?> modelClass;
    private final List<String> paths;
    private final List<Field[]> fields;

    @SuppressWarnings("unchecked")
    ModelProjection(ForgeSelector<?, R> selector, Class<?> modelClass, List<String> paths) {
        this.selector = (ForgeSelector<Object, R>) selector;
        this.modelClass = modelClass;
        this.paths = List.copyOf(paths);
        this.fields = new ArrayList<>();
        for (String path : paths) {
            fields.add(resolve(modelClass, path));
        }
    }

    @Override
    public List<String> getPaths() {
        return paths;
    }

    @Override
    public String describe(int index) {
        return "o." + paths.get(index) + " in the select lambda";
    }

    @Override
    public R map(Object[] values) {
        Object model = instantiate(modelClass);
        for (int i = 0; i < fields.size(); i++) {
            Field[] chain = fields.get(i);
            Object target = model;
            for (int j = 0; j < chain.length - 1; j++) {
                target = nested(target, chain[j]);
            }
            Field leaf = chain[chain.length - 1];
            if (values[i] != null || !leaf.getType().isPrimitive()) {
                set(leaf, target, values[i]);
            }
        }
        return selector.select(model);
    }

    private static Object nested(Object target, Field field) {
        try {
            Object value = field.get(target);
            if (value == null) {
                value = instantiate(field.getType());
                field.set(target, value);
            }
            return value;
        } catch (IllegalAccessException e) {
            throw new QueryForgeUnsupportedExpressionException("Field " + field + " is not accessible", e);
        }
    }

    private static void set(Field field, Object target, Object value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            throw new QueryForgeUnsupportedExpressionException("Value " + value + " can not be assigned to " + field, e);
        }
    }

    private static Object instantiate(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new QueryForgeUnsupportedExpressionException("Query model " + type.getName() + " can not be instantiated", e);
        }
    }

    private static Field[] resolve(Class<?> modelClass, String path) {
        String[] segments = path.split("\\.");
        Field[] chain = new Field[segments.length];
        Class<?> type = modelClass;
        for (int i = 0; i < segments.length; i++) {
            try {
                chain[i] = type.getField(segments[i]);
            } catch (NoSuchFieldException e) {
                throw new QueryForgeUnsupportedExpressionException("Field " + segments[i] + " not found in " + type.getName(), e);
            }
            type = chain[i].getType();
        }
        return chain;
    }
}
