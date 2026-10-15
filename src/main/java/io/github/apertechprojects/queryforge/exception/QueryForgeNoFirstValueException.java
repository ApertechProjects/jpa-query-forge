package io.github.apertechprojects.queryforge.exception;

public class QueryForgeNoFirstValueException extends QueryForgeException {

    public QueryForgeNoFirstValueException(String errorMessage) {
        super(errorMessage);
    }

    public QueryForgeNoFirstValueException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
