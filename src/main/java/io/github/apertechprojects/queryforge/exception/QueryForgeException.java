package io.github.apertechprojects.queryforge.exception;

/**
 * Base type for all exceptions thrown by jpa-query-forge.
 */
public class QueryForgeException extends RuntimeException {

    public QueryForgeException(String message) {
        super(message);
    }

    public QueryForgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
