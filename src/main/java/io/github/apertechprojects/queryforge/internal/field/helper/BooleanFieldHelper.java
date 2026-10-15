package io.github.apertechprojects.queryforge.internal.field.helper;


public class BooleanFieldHelper implements IFieldHelper<Boolean> {
    @Override
    public Boolean fillRandom() {
        return random.nextBoolean();
    }

    @Override
    public Boolean fillValue(String value) {
        return Boolean.parseBoolean(value);
    }

    @Override
    public String createGeneratorCode(String value) {
        return value.toLowerCase();
    }
}
