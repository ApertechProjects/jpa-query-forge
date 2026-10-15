package io.github.apertechprojects.queryforge.exception;


public class QueryForgeNoAvailableOperationException extends QueryForgeException {

    public QueryForgeNoAvailableOperationException(String errorMessage) {
        super(errorMessage);
    }

    public QueryForgeNoAvailableOperationException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
