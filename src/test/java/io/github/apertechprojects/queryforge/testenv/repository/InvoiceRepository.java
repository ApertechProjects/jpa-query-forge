package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.finance.Invoice;
import io.github.apertechprojects.queryforge.testenv.entity.finance.QInvoice;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaQueryForgeRepository<Invoice, String, QInvoice> {
}
