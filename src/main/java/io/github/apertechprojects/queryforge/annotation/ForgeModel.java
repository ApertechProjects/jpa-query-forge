package io.github.apertechprojects.queryforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Result class of {@code select(Dto.class)}. Every field is selected from the entity attribute with the same
 * name, or from the path given with {@link ForgeField} ({@code "item.name"}); {@link ForgeIgnore} skips a field,
 * {@link ForgeSubModel} fills a nested object. Fields of a superclass are included when it is a
 * {@code @ForgeModel} too. Collection fields and other object fields without {@code @ForgeSubModel} are skipped.
 * <p>
 * With {@link #entity()} set, the annotation processor checks every path and type at compile time.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ForgeModel {

    /** Entity this model is selected from; enables the compile-time check. */
    Class<?> entity() default void.class;

    /**
     * Fields of superclasses that are not {@code @ForgeModel} (shared base DTOs) to select as well, each from the
     * entity attribute with the same name: {@code inherited = "createdDate"}.
     */
    String[] inherited() default {};
}
