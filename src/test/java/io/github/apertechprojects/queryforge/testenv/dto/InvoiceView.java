package io.github.apertechprojects.queryforge.testenv.dto;

import io.github.apertechprojects.queryforge.annotation.ForgeField;
import io.github.apertechprojects.queryforge.annotation.ForgeIgnore;
import io.github.apertechprojects.queryforge.annotation.ForgeModel;
import io.github.apertechprojects.queryforge.annotation.ForgeSubModel;
import io.github.apertechprojects.queryforge.testenv.entity.finance.Invoice;

import java.math.BigDecimal;
import java.util.List;

@ForgeModel(entity = Invoice.class)
public class InvoiceView extends InvoiceBaseView {

    private String number;
    private BigDecimal amount = BigDecimal.ONE;
    @ForgeField("customer.name")
    private String customerName;
    @ForgeField("createdBy.name")
    private String createdBy;
    @ForgeField("customer.age")
    private String customerAge;
    @ForgeSubModel("customer")
    private CustomerPart customer;
    @ForgeIgnore
    private Integer lineNumber;
    private List<String> notes;

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCustomerAge() {
        return customerAge;
    }

    public CustomerPart getCustomer() {
        return customer;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public List<String> getNotes() {
        return notes;
    }

    public static class CustomerPart {
        private String name;
        private Integer age;

        public String getName() {
            return name;
        }

        public Integer getAge() {
            return age;
        }
    }
}
