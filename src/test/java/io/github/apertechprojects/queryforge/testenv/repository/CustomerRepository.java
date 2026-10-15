package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.ForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.Customer;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends ForgeRepository<Customer, Long> {
}
