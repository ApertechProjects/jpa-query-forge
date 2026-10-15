package io.github.apertechprojects.queryforge.internal.field.helper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class ZonedDateTimeFieldHelper implements IFieldHelper<ZonedDateTime> {
    @Override
    public ZonedDateTime fillRandom() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(System.currentTimeMillis() + random.nextInt(1000000000)), ZoneId.systemDefault());
    }

    @Override
    public ZonedDateTime fillValue(String value) {
        return ZonedDateTime.parse(value);
    }

    @Override
    public String createGeneratorCode(String value) {
        return "ZonedDateTime.parse(\"" + value + "\")";
    }
}
