package io.github.apertechprojects.queryforge.internal.field.helper;


import java.time.Instant;

public class InstantFieldHelper implements IFieldHelper<Instant> {
    @Override
    public Instant fillRandom() {
        return Instant.ofEpochMilli(System.currentTimeMillis() + random.nextInt(1000000000));
    }

    @Override
    public Instant fillValue(String value) {
        return Instant.parse(value);
    }

    @Override
    public String createGeneratorCode(String value) {
        return "Instant.parse(\"" + value + "\")";
    }
}
