package io.github.apertechprojects.queryforge.exception;

public class QueryForgeNoAvailableEnumException extends QueryForgeException {

    public QueryForgeNoAvailableEnumException(String errorMessage) {
        super(errorMessage);
    }

    public QueryForgeNoAvailableEnumException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
