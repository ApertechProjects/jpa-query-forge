package io.github.apertechprojects.queryforge.exception;

public class QueryForgeNoAvailableValueException extends QueryForgeException {
    public QueryForgeNoAvailableValueException(String errorMessage) {
        super(errorMessage);
    }

    public QueryForgeNoAvailableValueException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
