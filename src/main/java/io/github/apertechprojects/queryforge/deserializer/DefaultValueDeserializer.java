package io.github.apertechprojects.queryforge.deserializer;

import io.github.apertechprojects.queryforge.internal.util.ReflectionUtil;

public class DefaultValueDeserializer implements ValueDeserializer {
    @Override
    public <T> T deserialize(Object value, Class<T> clazz) throws Exception {
        return ReflectionUtil.deserializeObject(value.toString(), clazz);
    }
}
