package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.RoleAuthorization;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.QRoleAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAuthorizationRepository extends JpaRepository<RoleAuthorization, Long>, JpaQueryForgeRepository<RoleAuthorization, Long, QRoleAuthorization> {}
