package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.AdminUser;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.QAdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long>, JpaQueryForgeRepository<AdminUser, Long, QAdminUser> {}
