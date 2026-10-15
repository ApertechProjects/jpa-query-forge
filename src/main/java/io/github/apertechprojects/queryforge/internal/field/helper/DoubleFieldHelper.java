package io.github.apertechprojects.queryforge.internal.field.helper;


public class DoubleFieldHelper implements IFieldHelper<Double> {
    @Override
    public Double fillRandom() {
        return random.nextDouble();
    }

    @Override
    public Double fillValue(String value) {
        return Double.parseDouble(value);
    }

    @Override
    public String createGeneratorCode(String value) {
        return value + "d";
    }
}
