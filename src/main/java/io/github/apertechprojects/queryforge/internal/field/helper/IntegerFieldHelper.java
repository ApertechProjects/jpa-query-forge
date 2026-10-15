package io.github.apertechprojects.queryforge.internal.field.helper;


public class IntegerFieldHelper implements IFieldHelper<Integer> {
    @Override
    public Integer fillRandom() {
        return random.nextInt();
    }

    @Override
    public Integer fillValue(String value) {
        return Integer.parseInt(value);
    }

    @Override
    public String createGeneratorCode(String value) {
        return value;
    }
}
