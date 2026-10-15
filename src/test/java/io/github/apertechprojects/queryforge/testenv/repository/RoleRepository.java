package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long>, JpaQueryForgeRepository<Role, Long> {}
