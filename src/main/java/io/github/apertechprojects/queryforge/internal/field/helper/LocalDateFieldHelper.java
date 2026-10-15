package io.github.apertechprojects.queryforge.internal.field.helper;


import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class LocalDateFieldHelper implements IFieldHelper<LocalDate> {
    @Override
    public LocalDate fillRandom() {
        return Instant.ofEpochMilli(System.currentTimeMillis() + random.nextInt(1000000000))
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    @Override
    public LocalDate fillValue(String value) {
        return LocalDate.parse(value);
    }

    @Override
    public String createGeneratorCode(String value) {
        return "LocalDate.parse(\"" + value + "\")";
    }
}
