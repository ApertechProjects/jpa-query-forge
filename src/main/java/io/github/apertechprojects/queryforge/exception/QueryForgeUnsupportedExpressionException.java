package io.github.apertechprojects.queryforge.exception;

/**
 * A where lambda contains something that can not be translated to a JPA predicate.
 */
public class QueryForgeUnsupportedExpressionException extends QueryForgeException {

    public QueryForgeUnsupportedExpressionException(String message) {
        super(message);
    }

    public QueryForgeUnsupportedExpressionException(String message, Throwable cause) {
        super(message, cause);
    }
}
