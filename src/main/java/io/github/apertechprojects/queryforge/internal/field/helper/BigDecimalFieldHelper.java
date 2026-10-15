package io.github.apertechprojects.queryforge.internal.field.helper;


import java.math.BigDecimal;

public class BigDecimalFieldHelper implements IFieldHelper<BigDecimal> {
    @Override
    public BigDecimal fillRandom() {
        return BigDecimal.valueOf(random.nextDouble());
    }

    @Override
    public BigDecimal fillValue(String value) {
        return new BigDecimal(value);
    }

    @Override
    public String createGeneratorCode(String value) {
        return "new BigDecimal(\"" + value + "\");";
    }
}
