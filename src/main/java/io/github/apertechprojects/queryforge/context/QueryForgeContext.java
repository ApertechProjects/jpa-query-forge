package io.github.apertechprojects.queryforge.context;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

/**
 * Bridges Spring beans to {@code JpaQueryForgeRepository} default methods, which have no access to injection.
 * <p>
 * The {@link EntityManagerProvider} is resolved lazily on first use, so a user-defined bean always wins
 * regardless of configuration ordering.
 */
public class QueryForgeContext implements ApplicationContextAware, DisposableBean {

    private static volatile QueryForgeContext current;

    private ApplicationContext applicationContext;
    private volatile EntityManagerProvider entityManagerProvider;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        current = this;
    }

    @Override
    public void destroy() {
        if (current == this) {
            current = null;
        }
    }

    /** Entity manager of the active context. */
    public static EntityManager entityManager() {
        QueryForgeContext context = current;
        if (context == null) {
            throw new IllegalStateException("jpa-query-forge is not initialized: no QueryForgeContext bean found. "
                    + "Make sure Spring Boot auto-configuration is enabled or add @EnableQueryForge to a configuration class.");
        }
        return context.getEntityManager();
    }

    public EntityManager getEntityManager() {
        EntityManagerProvider provider = entityManagerProvider;
        if (provider == null) {
            ObjectProvider<EntityManagerProvider> custom = applicationContext.getBeanProvider(EntityManagerProvider.class);
            provider = custom.getIfAvailable(this::sharedEntityManagerProvider);
            entityManagerProvider = provider;
        }
        return provider.provide();
    }

    private EntityManagerProvider sharedEntityManagerProvider() {
        EntityManagerFactory entityManagerFactory = applicationContext.getBean(EntityManagerFactory.class);
        EntityManager shared = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        return () -> shared;
    }
}
