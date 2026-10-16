package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.JpaQueryForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.Authorization;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.QAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationRepository extends JpaRepository<Authorization, Long>, JpaQueryForgeRepository<Authorization, Long, QAuthorization> {}
