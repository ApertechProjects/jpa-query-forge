package io.github.apertechprojects.queryforge.expression;

import io.github.apertechprojects.queryforge.BaseTestInstance;
import io.github.apertechprojects.queryforge.TestApplication;
import io.github.apertechprojects.queryforge.exception.QueryForgeMappingException;
import io.github.apertechprojects.queryforge.exception.QueryForgeUnsupportedExpressionException;
import io.github.apertechprojects.queryforge.testenv.entity.Customer;
import io.github.apertechprojects.queryforge.testenv.entity.User;
import io.github.apertechprojects.queryforge.testenv.config.SqlRecorder;
import io.github.apertechprojects.queryforge.testenv.dto.BrokenViews;
import io.github.apertechprojects.queryforge.testenv.dto.InvoiceRecordView;
import io.github.apertechprojects.queryforge.testenv.dto.InvoiceAuditView;
import io.github.apertechprojects.queryforge.testenv.dto.InvoiceView;
import io.github.apertechprojects.queryforge.testenv.entity.finance.Invoice;
import io.github.apertechprojects.queryforge.testenv.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestApplication.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectionAndAggregateTest extends BaseTestInstance {

    @Autowired
    private InvoiceRepository invoiceRepository;

    public record InvoiceRow(String number, BigDecimal amount, String customerName) {
    }

    /** Setter style DTO, like most procurementservice DTOs. */
    public static class InvoiceDto {
        private String number;
        private BigDecimal openAmount = BigDecimal.ZERO;
        private String customerName;
        private String createdBy;

        public String getNumber() {
            return number;
        }

        public void setNumber(String number) {
            this.number = number;
        }

        public BigDecimal getOpenAmount() {
            return openAmount;
        }

        public void setOpenAmount(BigDecimal openAmount) {
            this.openAmount = openAmount;
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
    }

    /** Builder style DTO. */
    public record InvoiceSummary(String number, LocalDate dueDate) {
        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            private String number;
            private LocalDate dueDate;

            Builder number(String number) {
                this.number = number;
                return this;
            }

            Builder dueDate(LocalDate dueDate) {
                this.dueDate = dueDate;
                return this;
            }

            InvoiceSummary build() {
                return new InvoiceSummary(number, dueDate);
            }
        }
    }

    @BeforeAll
    void createInvoices() {
        if (invoiceRepository.count() > 0) {
            return;
        }
        User name1 = userRepository.firstOrDefault(u -> u.name.equals("Name 1")).orElseThrow();
        User name2 = userRepository.firstOrDefault(u -> u.name.equals("Name 2")).orElseThrow();
        Customer age20 = customerRepository.firstOrDefault(c -> c.age == 20).orElseThrow();
        Customer age21 = customerRepository.firstOrDefault(c -> c.age == 21).orElseThrow();
        Invoice first = new Invoice("i1", "INV-1", new BigDecimal("100.50"), new BigDecimal("100.50"), LocalDate.of(2026, 1, 10), age20, name1);
        Invoice second = new Invoice("i2", "INV-2", new BigDecimal("250.00"), new BigDecimal("0.00"), LocalDate.of(2026, 2, 15), age21, name2);
        second.setOriginal(first);
        invoiceRepository.saveAll(List.of(
                first,
                second,
                new Invoice("i3", "INV-3", new BigDecimal("75.25"), new BigDecimal("20.00"), LocalDate.of(2026, 3, 1), null, name1),
                new Invoice("i4", "INV-4", new BigDecimal("250.00"), new BigDecimal("250.00"), LocalDate.of(2026, 3, 20), age20, name2)));
    }

    @Test
    void selectIntoRecord() {
        List<InvoiceRow> rows = invoiceRepository.where(i -> i.amount.compareTo(new BigDecimal("100")) > 0)
                .orderBy(Invoice::getNumber)
                .select(i -> new InvoiceRow(i.number, i.amount, i.customer.name))
                .toList();
        assertEquals(List.of(
                new InvoiceRow("INV-1", new BigDecimal("100.50"), "Customer 1"),
                new InvoiceRow("INV-2", new BigDecimal("250.00"), "Customer 2"),
                new InvoiceRow("INV-4", new BigDecimal("250.00"), "Customer 1")), rows);
    }

    @Test
    void selectWithSettersAndComputedValues() {
        List<InvoiceDto> rows = invoiceRepository.orderBy(i -> i.number).select(i -> {
            InvoiceDto dto = new InvoiceDto();
            dto.setNumber(i.number);
            dto.setOpenAmount(i.amount.subtract(i.paidAmount));
            dto.setCustomerName(i.customer.name);
            dto.setCreatedBy(i.createdBy.name + " " + i.createdBy.surname);
            return dto;
        }).toList();

        assertEquals(List.of("INV-1", "INV-2", "INV-3", "INV-4"), rows.stream().map(InvoiceDto::getNumber).toList());
        assertEquals(0, new BigDecimal("55.25").compareTo(rows.get(2).getOpenAmount()));
        assertNull(rows.get(2).getCustomerName(), "LEFT join: invoice without customer is kept");
        assertEquals("Name 2 Surname 1", rows.get(1).getCreatedBy());
    }

    @Test
    void selectWithBuilderAndSingleValues() {
        assertEquals(List.of(new InvoiceSummary("INV-3", LocalDate.of(2026, 3, 1)), new InvoiceSummary("INV-4", LocalDate.of(2026, 3, 20))),
                invoiceRepository.where(i -> i.dueDate.isAfter(LocalDate.of(2026, 2, 28)))
                        .orderBy(i -> i.dueDate)
                        .select(i -> InvoiceSummary.builder().number(i.number).dueDate(i.dueDate).build())
                        .toList());
        assertEquals(List.of("INV-4", "INV-3"), invoiceRepository.orderByDescending(Invoice::getDueDate).take(2).select(i -> i.number).toList());
        assertEquals(Optional.of("INV-2"), invoiceRepository.where(i -> i.id.equals("i2")).select(i -> i.number).firstOrDefault());
    }

    @Test
    void distinctSelect() {
        assertEquals(List.of(new BigDecimal("75.25"), new BigDecimal("100.50"), new BigDecimal("250.00")),
                invoiceRepository.orderBy(i -> i.amount).select(i -> i.amount).distinct().toList());
        assertEquals(List.of("Name 1", "Name 2"), invoiceRepository.orderBy(i -> i.createdBy.name).select(i -> i.createdBy.name).distinct().toList());
        // the sort key is added to the select list, so rows that differ only in it stay apart
        assertEquals(4, invoiceRepository.orderBy(i -> i.number).select(i -> i.createdBy.name).distinct().toList().size());
    }

    @Test
    void selectPage() {
        Page<String> page = invoiceRepository.orderBy(Invoice::getNumber).select(i -> i.number).toPage(PageRequest.of(1, 3));
        assertEquals(List.of("INV-4"), page.getContent());
        assertEquals(4, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void bigDecimalArithmeticAndDatesInConditions() {
        assertEquals(List.of("INV-2", "INV-3"), invoiceRepository
                .where(i -> i.amount.subtract(i.paidAmount).compareTo(BigDecimal.ZERO) > 0)
                .orderBy(i -> i.number).select(i -> i.number).toList());
        assertEquals(List.of("INV-1", "INV-2"), invoiceRepository
                .where(i -> i.dueDate.isBefore(LocalDate.of(2026, 3, 1)))
                .orderBy(i -> i.number).select(i -> i.number).toList());
        assertEquals(List.of("INV-2", "INV-4"), invoiceRepository
                .where(i -> i.createdBy.name.equals("Name 2"))
                .orderBy(i -> i.number).select(i -> i.number).toList());
    }

    @Test
    void aggregates() {
        assertEquals(0, new BigDecimal("675.75").compareTo(invoiceRepository.query().sum(i -> i.amount)));
        assertEquals(0, new BigDecimal("20.00").compareTo(invoiceRepository.where(i -> i.customer == null).sum(i -> i.paidAmount)));
        assertEquals(0, BigDecimal.ZERO.compareTo(invoiceRepository.where(i -> i.number.equals("none")).sum(i -> i.amount)));
        assertEquals(Integer.valueOf(188), customerRepository.query().sum(c -> c.age));

        assertEquals(Optional.of(LocalDate.of(2026, 3, 20)), invoiceRepository.query().max(i -> i.dueDate));
        assertEquals(0, new BigDecimal("75.25").compareTo(invoiceRepository.query().min(i -> i.amount).orElseThrow()));
        assertEquals(168.9375, invoiceRepository.query().average(i -> i.amount).orElseThrow(), 1e-9);
        assertTrue(invoiceRepository.where(i -> i.number.equals("none")).max(i -> i.dueDate).isEmpty());
        assertTrue(invoiceRepository.where(i -> i.number.equals("none")).average(i -> i.amount).isEmpty());
    }

    @Test
    void anyAndCount() {
        assertTrue(invoiceRepository.any(i -> i.number.equals("INV-3")));
        assertFalse(invoiceRepository.any(i -> i.number.equals("none")));
        assertEquals(2, invoiceRepository.count(i -> i.customer.name.equals("Customer 1")));
        assertEquals(4, invoiceRepository.orderBy(i -> i.number).select(i -> i.number).count());
    }

    @Test
    void requiredAssociationsAreInnerJoinsOptionalOnesLeftJoins() {
        SqlRecorder.clear();
        assertEquals(4, invoiceRepository.select(i -> i.createdBy.name + "/" + i.customer.name).toList().size());
        String sql = SqlRecorder.statements().get(SqlRecorder.statements().size() - 1).toLowerCase();
        assertTrue(sql.matches("(?s).*(?<!left )join test_user .*"), sql);
        assertTrue(sql.contains("left join customer"), sql);
    }

    @Test
    void associationsAfterAnOptionalOneStayLeftJoins() {
        SqlRecorder.clear();
        List<String> rows = invoiceRepository.orderBy(i -> i.number).select(i -> i.number + ":" + i.original.createdBy.name).toList();
        assertEquals(List.of("INV-1:null", "INV-2:Name 1", "INV-3:null", "INV-4:null"), rows);
        String sql = SqlRecorder.statements().get(SqlRecorder.statements().size() - 1).toLowerCase();
        assertTrue(sql.contains("left join test_user"), sql);
    }

    @Test
    void selectIntoForgeModel() {
        List<InvoiceView> rows = invoiceRepository.orderBy(Invoice::getNumber).select(InvoiceView.class).toList();
        assertEquals(List.of("i1", "i2", "i3", "i4"), rows.stream().map(InvoiceView::getId).toList());
        InvoiceView first = rows.get(0);
        assertEquals("INV-1", first.getNumber());
        assertEquals(0, new BigDecimal("100.50").compareTo(first.getAmount()));
        assertEquals("Customer 1", first.getCustomerName());
        assertEquals("Name 1", first.getCreatedBy());
        assertEquals("20", first.getCustomerAge());
        assertEquals("Customer 1", first.getCustomer().getName());
        assertEquals(20, first.getCustomer().getAge());
        assertNull(first.getLineNumber());
        assertNull(first.getNotes());
        assertNull(rows.get(2).getCustomerName(), "LEFT join for the optional customer");

        assertEquals(List.of(new InvoiceRecordView("INV-4", new BigDecimal("250.00"), "Name 2")),
                invoiceRepository.where(i -> i.number.equals("INV-4")).select(InvoiceRecordView.class).toList());
        Page<InvoiceRecordView> page = invoiceRepository.orderBy(Invoice::getNumber).select(InvoiceRecordView.class).toPage(1, 3);
        assertEquals(4, page.getTotalElements());
        assertEquals("INV-4", page.getContent().get(0).number());
        assertEquals(Optional.of("INV-2"), invoiceRepository.where(i -> i.id.equals("i2")).select(InvoiceView.class).firstOrDefault().map(InvoiceView::getNumber));
    }

    @Test
    void inheritedFieldsOfPlainBaseClasses() {
        List<InvoiceAuditView> rows = invoiceRepository.orderBy(Invoice::getNumber).select(InvoiceAuditView.class).toList();
        assertEquals(List.of("i1", "i2", "i3", "i4"), rows.stream().map(InvoiceAuditView::getId).toList());
        assertEquals("INV-1", rows.get(0).getNumber());
        assertNotNull(rows.get(0).getCreatedDate());
        assertNull(rows.get(0).getNote());
    }

    @Test
    void forgeModelErrorsNameTheField() {
        assertThrows(QueryForgeMappingException.class, () -> invoiceRepository.select(BrokenViews.NotAnnotated.class));
        QueryForgeMappingException unknown = assertThrows(QueryForgeMappingException.class,
                () -> invoiceRepository.select(BrokenViews.UnknownPath.class).toList());
        assertTrue(unknown.getMessage().contains("UnknownPath.customerName") && unknown.getMessage().contains("customer.nmae"), unknown.getMessage());
        QueryForgeMappingException wrongType = assertThrows(QueryForgeMappingException.class,
                () -> invoiceRepository.select(BrokenViews.WrongType.class).toList());
        assertTrue(wrongType.getMessage().contains("java.time.LocalDate can not be assigned to java.math.BigDecimal"), wrongType.getMessage());
    }

    @Test
    void unsupportedSelects() {
        assertThrows(QueryForgeUnsupportedExpressionException.class, () -> invoiceRepository.select(i -> i.customer));
        assertThrows(QueryForgeUnsupportedExpressionException.class, () -> invoiceRepository.select(i -> i));
        assertThrows(QueryForgeUnsupportedExpressionException.class, () -> invoiceRepository.select(i -> "constant"));
    }
}
