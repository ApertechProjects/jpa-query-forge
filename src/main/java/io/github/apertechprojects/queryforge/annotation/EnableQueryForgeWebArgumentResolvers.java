package io.github.apertechprojects.queryforge.annotation;

import io.github.apertechprojects.queryforge.web.QueryForgeWebMvcConfigurer;
import io.github.apertechprojects.queryforge.web.CriteriaListArgumentResolver;
import io.github.apertechprojects.queryforge.web.ForgeQueryArgumentResolver;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Import({QueryForgeWebMvcConfigurer.class, CriteriaListArgumentResolver.class, ForgeQueryArgumentResolver.class})
@Target({ElementType.TYPE})
@EnableQueryForge
public @interface EnableQueryForgeWebArgumentResolvers {
}
