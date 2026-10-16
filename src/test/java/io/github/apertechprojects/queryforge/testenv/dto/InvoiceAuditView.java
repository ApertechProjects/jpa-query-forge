package io.github.apertechprojects.queryforge.testenv.dto;

import io.github.apertechprojects.queryforge.annotation.ForgeModel;
import io.github.apertechprojects.queryforge.testenv.entity.finance.Invoice;

@ForgeModel(entity = Invoice.class, inherited = {"id", "createdDate"})
public class InvoiceAuditView extends AuditBase {

    private String number;

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }
}
