package io.github.apertechprojects.queryforge.testenv.dto;

import io.github.apertechprojects.queryforge.annotation.ForgeField;
import io.github.apertechprojects.queryforge.annotation.ForgeModel;
import io.github.apertechprojects.queryforge.testenv.entity.finance.Invoice;

import java.math.BigDecimal;

@ForgeModel(entity = Invoice.class)
public record InvoiceRecordView(String number, BigDecimal amount, @ForgeField("createdBy.name") String createdBy) {
}
