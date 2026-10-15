package io.github.apertechprojects.queryforge.autoconfigure;

import io.github.apertechprojects.queryforge.context.QueryForgeContext;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Registers the {@link QueryForgeContext}. Repositories need no extra configuration:
 * extending {@code ForgeRepository} is enough.
 */
@AutoConfiguration
@ConditionalOnClass({EntityManager.class, JpaRepository.class})
public class QueryForgeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public static QueryForgeContext queryForgeContext() {
        return new QueryForgeContext();
    }
}
