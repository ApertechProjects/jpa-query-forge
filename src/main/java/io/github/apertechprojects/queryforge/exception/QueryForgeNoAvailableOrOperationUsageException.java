package io.github.apertechprojects.queryforge.exception;


public class QueryForgeNoAvailableOrOperationUsageException extends QueryForgeException {

    public QueryForgeNoAvailableOrOperationUsageException(String errorMessage) {
        super(errorMessage);
    }

    public QueryForgeNoAvailableOrOperationUsageException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
