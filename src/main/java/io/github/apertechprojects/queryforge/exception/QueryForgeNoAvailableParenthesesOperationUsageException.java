package io.github.apertechprojects.queryforge.exception;


public class QueryForgeNoAvailableParenthesesOperationUsageException extends QueryForgeException {

    public QueryForgeNoAvailableParenthesesOperationUsageException(String errorMessage) {
        super(errorMessage);
    }

    public QueryForgeNoAvailableParenthesesOperationUsageException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
