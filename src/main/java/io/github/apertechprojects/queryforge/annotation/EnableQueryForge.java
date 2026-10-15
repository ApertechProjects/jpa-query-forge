package io.github.apertechprojects.queryforge.annotation;

import io.github.apertechprojects.queryforge.autoconfigure.QueryForgeAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicitly enables jpa-query-forge. Optional with Spring Boot, where auto-configuration does the same.
 * <p>
 * Unlike {@code @EnableJpaRepositories}, it does not change repository scanning, so it can live on any
 * configuration class in any package.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Import(QueryForgeAutoConfiguration.class)
public @interface EnableQueryForge {
}
