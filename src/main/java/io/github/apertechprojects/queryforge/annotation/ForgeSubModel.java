package io.github.apertechprojects.queryforge.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link ForgeModel} field holding a nested model. Its fields are selected below {@link #value()}
 * ({@code @ForgeSubModel("vendor") VendorRow vendor} selects {@code vendor.name}, ...); empty means the same entity.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ForgeSubModel {

    String value() default "";
}
