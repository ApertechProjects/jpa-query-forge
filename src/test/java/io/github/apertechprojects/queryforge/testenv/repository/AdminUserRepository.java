package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.ForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long>, ForgeRepository<AdminUser, Long> {}
