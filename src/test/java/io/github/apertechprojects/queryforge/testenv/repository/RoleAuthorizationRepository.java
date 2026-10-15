package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.ForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.RoleAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAuthorizationRepository extends JpaRepository<RoleAuthorization, Long>, ForgeRepository<RoleAuthorization, Long> {}
