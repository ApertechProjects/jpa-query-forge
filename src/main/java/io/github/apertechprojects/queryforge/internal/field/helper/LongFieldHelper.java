package io.github.apertechprojects.queryforge.internal.field.helper;


public class LongFieldHelper implements IFieldHelper<Long> {
    @Override
    public Long fillRandom() {
        return random.nextLong();
    }

    @Override
    public Long fillValue(String value) {
        return Long.parseLong(value);
    }

    @Override
    public String createGeneratorCode(String value) {
        return value + "L";
    }
}
