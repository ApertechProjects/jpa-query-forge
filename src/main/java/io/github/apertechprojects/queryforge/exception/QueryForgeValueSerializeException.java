package io.github.apertechprojects.queryforge.exception;

public class QueryForgeValueSerializeException extends QueryForgeException {
    public QueryForgeValueSerializeException(String errorMessage) {
        super(errorMessage);
    }

    public QueryForgeValueSerializeException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
