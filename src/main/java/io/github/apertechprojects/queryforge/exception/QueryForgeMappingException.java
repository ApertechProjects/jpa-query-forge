package io.github.apertechprojects.queryforge.exception;

/**
 * A {@code @ForgeModel} class does not match the entity it is selected from.
 */
public class QueryForgeMappingException extends QueryForgeException {

    public QueryForgeMappingException(String message) {
        super(message);
    }

    public QueryForgeMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
