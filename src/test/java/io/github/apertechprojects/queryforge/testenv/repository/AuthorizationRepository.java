package io.github.apertechprojects.queryforge.testenv.repository;

import io.github.apertechprojects.queryforge.repository.ForgeRepository;
import io.github.apertechprojects.queryforge.testenv.entity.authorization.Authorization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthorizationRepository extends JpaRepository<Authorization, Long>, ForgeRepository<Authorization, Long> {}
