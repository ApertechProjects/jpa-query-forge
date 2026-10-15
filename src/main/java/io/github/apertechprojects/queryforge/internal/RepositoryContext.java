package io.github.apertechprojects.queryforge.internal;

import io.github.apertechprojects.queryforge.deserializer.ValueDeserializer;
import jakarta.persistence.EntityManager;

/**
 * Context object that holds dependencies required for dynamic query execution
 */
public class RepositoryContext {
    private final EntityManager entityManager;
    private final ValueDeserializer deserializer;

    public RepositoryContext(EntityManager entityManager, ValueDeserializer deserializer) {
        this.entityManager = entityManager;
        this.deserializer = deserializer;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public ValueDeserializer getDeserializer() {
        return deserializer;
    }
}

