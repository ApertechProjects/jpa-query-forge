package io.github.apertechprojects.queryforge.testenv.entity.finance;

import io.github.apertechprojects.queryforge.testenv.entity.Customer;
import io.github.apertechprojects.queryforge.testenv.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice")
public class Invoice extends AuditedEntity {

    @Id
    private String id;

    private String number;

    private BigDecimal amount;

    private BigDecimal paidAmount;

    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    private Invoice original;

    public Invoice() {
    }

    public Invoice(String id, String number, BigDecimal amount, BigDecimal paidAmount, LocalDate dueDate, Customer customer, User createdBy) {
        this.id = id;
        this.number = number;
        this.amount = amount;
        this.paidAmount = paidAmount;
        this.dueDate = dueDate;
        this.customer = customer;
        setCreatedBy(createdBy);
        setCreatedDate(LocalDateTime.of(2026, 1, 1, 9, 0));
    }

    public String getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setOriginal(Invoice original) {
        this.original = original;
    }
}
