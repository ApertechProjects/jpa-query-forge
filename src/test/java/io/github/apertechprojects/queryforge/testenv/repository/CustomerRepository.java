package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.Customer;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaQueryForgeRepository<Customer, Long> {
}
