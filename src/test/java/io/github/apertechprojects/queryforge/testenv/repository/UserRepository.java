package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.User;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaQueryForgeRepository<User, Long> {
}

