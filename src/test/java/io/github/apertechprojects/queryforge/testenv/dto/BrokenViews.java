package io.github.apertechprojects.queryforge.testenv.dto;

import io.github.apertechprojects.queryforge.annotation.ForgeField;
import io.github.apertechprojects.queryforge.annotation.ForgeModel;

import java.math.BigDecimal;

/** Models without {@code entity}, so they are only checked at runtime. */
public final class BrokenViews {

    private BrokenViews() {
    }

    public static class NotAnnotated {
        private String number;
    }

    @ForgeModel
    public static class UnknownPath {
        @ForgeField("customer.nmae")
        private String customerName;
    }

    @ForgeModel
    public static class WrongType {
        @ForgeField("dueDate")
        private BigDecimal dueDate;
    }
}
