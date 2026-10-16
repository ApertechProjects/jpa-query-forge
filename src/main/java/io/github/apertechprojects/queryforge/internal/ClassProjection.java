package io.github.apertechprojects.queryforge.internal;

import io.github.apertechprojects.queryforge.annotation.ForgeField;
import io.github.apertechprojects.queryforge.annotation.ForgeIgnore;
import io.github.apertechprojects.queryforge.annotation.ForgeModel;
import io.github.apertechprojects.queryforge.annotation.ForgeSubModel;
import io.github.apertechprojects.queryforge.exception.QueryForgeMappingException;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Projection onto a {@link ForgeModel} class: its fields define the selected paths, rows are written into new
 * instances through setters (or the fields, or the canonical constructor of a record).
 *
 * @param <R> model class
 */
public final class ClassProjection<R> implements Projection<R> {

    private static final ClassValue<ClassProjection<?>> CACHE = new ClassValue<>() {
        @Override
        protected ClassProjection<?> computeValue(Class<?> type) {
            return new ClassProjection<>(type);
        }
    };

    private final Model root;
    private final List<String> paths = new ArrayList<>();
    private final List<Slot> slots = new ArrayList<>();

    @SuppressWarnings("unchecked")
    public static <R> ClassProjection<R> of(Class<R> type) {
        return (ClassProjection<R>) CACHE.get(type);
    }

    private ClassProjection(Class<?> type) {
        if (!type.isAnnotationPresent(ForgeModel.class)) {
            throw new QueryForgeMappingException(type.getName() + " must be annotated with @ForgeModel to be selected");
        }
        this.root = model(type, "");
        if (paths.isEmpty()) {
            throw new QueryForgeMappingException(type.getName() + " has no field to select");
        }
    }

    @Override
    public List<String> getPaths() {
        return paths;
    }

    @Override
    public String describe(int index) {
        Slot slot = slots.get(index);
        return slot.field().getDeclaringClass().getName() + "." + slot.field().getName() + " (path " + paths.get(index) + ")";
    }

    @Override
    public void check(List<Class<?>> selectedTypes) {
        for (int i = 0; i < slots.size(); i++) {
            Class<?> source = selectedTypes.get(i);
            Class<?> target = slots.get(i).field().getType();
            if (source != null && !convertible(source, target)) {
                throw new QueryForgeMappingException(describe(i) + ": " + source.getName() + " can not be assigned to " + target.getName());
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public R map(Object[] values) {
        return (R) root.build(values);
    }

    // ------------------------------------------------------------------ model structure

    private record Slot(Field field, Method setter, int index) {
    }

    private interface Part {
        Field field();
    }

    private record Value(Field field, Method setter, int index) implements Part {
    }

    private record Nested(Field field, Method setter, Model model) implements Part {
    }

    private record Model(Class<?> type, List<Part> parts) {

        Object build(Object[] values) {
            if (type.isRecord()) {
                RecordComponent[] components = type.getRecordComponents();
                Object[] arguments = new Object[components.length];
                for (Part part : parts) {
                    int position = indexOf(components, part.field().getName());
                    arguments[position] = value(part, values);
                }
                for (int i = 0; i < components.length; i++) {
                    if (arguments[i] == null && components[i].getType().isPrimitive()) {
                        arguments[i] = defaultValue(components[i].getType());
                    }
                }
                try {
                    Constructor<?> constructor = type.getDeclaredConstructor(Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new));
                    constructor.setAccessible(true);
                    return constructor.newInstance(arguments);
                } catch (ReflectiveOperationException e) {
                    throw new QueryForgeMappingException("Record " + type.getName() + " can not be created", e);
                }
            }
            Object instance;
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                instance = constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new QueryForgeMappingException(type.getName() + " needs a no-argument constructor", e);
            }
            for (Part part : parts) {
                Object value = value(part, values);
                if (value == null && part.field().getType().isPrimitive()) {
                    continue;
                }
                Method setter = part instanceof Value v ? v.setter() : ((Nested) part).setter();
                try {
                    if (setter != null) {
                        setter.invoke(instance, value);
                    } else {
                        part.field().set(instance, value);
                    }
                } catch (ReflectiveOperationException | IllegalArgumentException e) {
                    throw new QueryForgeMappingException("Value " + value + " can not be written to " + part.field(), e);
                }
            }
            return instance;
        }

        private static Object value(Part part, Object[] values) {
            if (part instanceof Value value) {
                return convert(values[value.index()], value.field().getType());
            }
            return ((Nested) part).model().build(values);
        }

        private static int indexOf(RecordComponent[] components, String name) {
            for (int i = 0; i < components.length; i++) {
                if (components[i].getName().equals(name)) {
                    return i;
                }
            }
            throw new QueryForgeMappingException("Record component " + name + " not found");
        }
    }

    private Model model(Class<?> type, String prefix) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (current != type && !current.isAnnotationPresent(ForgeModel.class)) {
                break;
            }
            hierarchy.add(0, current);
        }
        List<Part> parts = new ArrayList<>();
        ForgeModel forgeModel = type.getAnnotation(ForgeModel.class);
        for (String name : forgeModel == null ? new String[0] : forgeModel.inherited()) {
            Field field = inheritedField(hierarchy.get(0).getSuperclass(), name);
            if (field == null) {
                throw new QueryForgeMappingException(type.getName() + ": inherited field " + name + " is not declared in a superclass");
            }
            field.trySetAccessible();
            Value value = new Value(field, setter(type, field), paths.size());
            paths.add(join(prefix, name));
            slots.add(new Slot(field, value.setter(), value.index()));
            parts.add(value);
        }
        for (Class<?> declaring : hierarchy) {
            for (Field field : declaring.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic() || field.isAnnotationPresent(ForgeIgnore.class)) {
                    continue;
                }
                field.trySetAccessible();
                Method setter = type.isRecord() ? null : setter(type, field);
                ForgeSubModel subModel = field.getAnnotation(ForgeSubModel.class);
                if (subModel != null) {
                    parts.add(new Nested(field, setter, model(field.getType(), join(prefix, subModel.value()))));
                } else if (isValueType(field.getType())) {
                    ForgeField forgeField = field.getAnnotation(ForgeField.class);
                    String path = join(prefix, forgeField != null ? forgeField.value() : field.getName());
                    Value value = new Value(field, setter, paths.size());
                    paths.add(path);
                    slots.add(new Slot(field, setter, value.index()));
                    parts.add(value);
                }
            }
        }
        return new Model(type, parts);
    }

    private static Field inheritedField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getName().equals(name) && !Modifier.isStatic(field.getModifiers())) {
                    return field;
                }
            }
        }
        return null;
    }

    private static Method setter(Class<?> type, Field field) {
        String name = "set" + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
        try {
            Method method = type.getMethod(name, field.getType());
            return Modifier.isStatic(method.getModifiers()) ? null : method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String join(String prefix, String path) {
        if (prefix.isEmpty()) {
            return path;
        }
        return path.isEmpty() ? prefix : prefix + "." + path;
    }

    // ------------------------------------------------------------------ values

    static boolean isValueType(Class<?> type) {
        Class<?> boxed = ClassUtils.resolvePrimitiveIfNecessary(type);
        return boxed == String.class || Number.class.isAssignableFrom(boxed) || boxed == Boolean.class || boxed == Character.class
                || Temporal.class.isAssignableFrom(boxed) || Date.class.isAssignableFrom(boxed) || boxed == UUID.class
                || boxed.isEnum() || boxed == byte[].class;
    }

    /** Assignable as is, after boxing, to {@code String}, or between numbers. */
    static boolean convertible(Class<?> source, Class<?> target) {
        Class<?> from = ClassUtils.resolvePrimitiveIfNecessary(source);
        Class<?> to = ClassUtils.resolvePrimitiveIfNecessary(target);
        return to.isAssignableFrom(from) || to == String.class || (Number.class.isAssignableFrom(from) && Number.class.isAssignableFrom(to));
    }

    static Object convert(Object value, Class<?> target) {
        if (value == null) {
            return null;
        }
        Class<?> to = ClassUtils.resolvePrimitiveIfNecessary(target);
        if (to.isInstance(value)) {
            return value;
        }
        if (to == String.class) {
            return value instanceof Enum<?> e ? e.name() : value.toString();
        }
        if (value instanceof Number number) {
            if (to == BigDecimal.class) {
                return new BigDecimal(number.toString());
            }
            if (to == BigInteger.class) {
                return new BigDecimal(number.toString()).toBigInteger();
            }
            if (to == Long.class) {
                return number.longValue();
            }
            if (to == Integer.class) {
                return number.intValue();
            }
            if (to == Short.class) {
                return number.shortValue();
            }
            if (to == Byte.class) {
                return number.byteValue();
            }
            if (to == Double.class) {
                return number.doubleValue();
            }
            if (to == Float.class) {
                return number.floatValue();
            }
        }
        throw new QueryForgeMappingException("Value " + value + " (" + value.getClass().getName() + ") can not be assigned to " + target.getName());
    }

    private static Object defaultValue(Class<?> primitive) {
        if (primitive == boolean.class) {
            return false;
        }
        if (primitive == char.class) {
            return '\0';
        }
        return convert(0, primitive);
    }
}
