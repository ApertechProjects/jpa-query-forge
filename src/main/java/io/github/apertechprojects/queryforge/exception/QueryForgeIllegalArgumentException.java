package io.github.apertechprojects.queryforge.exception;

public class QueryForgeIllegalArgumentException extends IllegalArgumentException {

    public QueryForgeIllegalArgumentException(String errorMessage) {
        super(errorMessage);
    }

    public QueryForgeIllegalArgumentException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
