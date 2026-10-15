package io.github.apertechprojects.queryforge.web;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class QueryForgeWebMvcConfigurer implements WebMvcConfigurer {

    private final CriteriaListArgumentResolver criteriaListArgumentResolver;
    private final ForgeQueryArgumentResolver forgeQueryArgumentResolver;

    public QueryForgeWebMvcConfigurer(CriteriaListArgumentResolver criteriaListArgumentResolver, ForgeQueryArgumentResolver forgeQueryArgumentResolver) {
        this.criteriaListArgumentResolver = criteriaListArgumentResolver;
        this.forgeQueryArgumentResolver = forgeQueryArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(criteriaListArgumentResolver);
        resolvers.add(forgeQueryArgumentResolver);
    }
}
