package io.github.apertechprojects.queryforge.internal.field.helper;


import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateFieldHelper implements IFieldHelper<Date> {
    @Override
    public Date fillRandom() {
        return new Date(System.currentTimeMillis() + random.nextInt(1000000000));
    }

    @Override
    public Date fillValue(String value) {
        DateFormat dateFormat = new SimpleDateFormat();
        try {
            return dateFormat.parse(value);
        } catch (ParseException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @Override
    public String createGeneratorCode(String value) {
        return "new Date(" + fillValue(value).getTime() + "L)";
    }
}
