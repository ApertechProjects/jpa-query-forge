package io.github.apertechprojects.queryforge.context;

import io.github.apertechprojects.queryforge.deserializer.DefaultValueDeserializer;
import io.github.apertechprojects.queryforge.deserializer.ValueDeserializer;
import io.github.apertechprojects.queryforge.internal.RepositoryContext;
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
 * Collaborators are resolved lazily on first use, so user-defined {@link ValueDeserializer} and
 * {@link EntityManagerProvider} beans always win regardless of configuration ordering.
 */
public class QueryForgeContext implements ApplicationContextAware, DisposableBean {

    private static volatile QueryForgeContext current;

    private ApplicationContext applicationContext;
    private volatile EntityManagerProvider entityManagerProvider;
    private volatile ValueDeserializer deserializer;

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

    public static RepositoryContext repositoryContext() {
        QueryForgeContext context = current;
        if (context == null) {
            throw new IllegalStateException("jpa-query-forge is not initialized: no QueryForgeContext bean found. "
                    + "Make sure Spring Boot auto-configuration is enabled or add @EnableQueryForge to a configuration class.");
        }
        return new RepositoryContext(context.getEntityManager(), context.getDeserializer());
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

    public ValueDeserializer getDeserializer() {
        ValueDeserializer result = deserializer;
        if (result == null) {
            result = applicationContext.getBeanProvider(ValueDeserializer.class).getIfAvailable(DefaultValueDeserializer::new);
            deserializer = result;
        }
        return result;
    }

    private EntityManagerProvider sharedEntityManagerProvider() {
        EntityManagerFactory entityManagerFactory = applicationContext.getBean(EntityManagerFactory.class);
        EntityManager shared = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        return () -> shared;
    }
}
