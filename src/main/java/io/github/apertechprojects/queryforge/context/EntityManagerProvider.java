package io.github.apertechprojects.queryforge.context;

import jakarta.persistence.EntityManager;

/**
 * Supplies the {@link EntityManager} used to run forge queries.
 * <p>
 * By default the shared entity manager of the primary {@code EntityManagerFactory} is used.
 * Declare a bean of this type to override it (e.g. for a secondary persistence unit).
 */
@FunctionalInterface
public interface EntityManagerProvider {
    EntityManager provide();
}
