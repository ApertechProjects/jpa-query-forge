package io.github.apertechprojects.queryforge.internal.field.helper;

import java.util.Random;

public interface IFieldHelper<T> {
    static final Random random = new Random();

    T fillRandom();

    T fillValue(String value);

    String createGeneratorCode(String value);
}
