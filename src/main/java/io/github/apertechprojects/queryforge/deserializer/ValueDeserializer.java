package io.github.apertechprojects.queryforge.deserializer;

public interface ValueDeserializer {
    <T> T deserialize(Object value, Class<T> clazz) throws Exception;
}
