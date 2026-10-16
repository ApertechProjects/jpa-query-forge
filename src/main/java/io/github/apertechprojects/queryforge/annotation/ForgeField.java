package io.github.apertechprojects.queryforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Attribute path a {@link ForgeModel} field is selected from, e.g. {@code "item.name"} or {@code "createdBy.fullName"}. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ForgeField {

    String value();
}
