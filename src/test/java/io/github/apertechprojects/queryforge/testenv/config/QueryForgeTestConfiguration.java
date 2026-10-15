package io.github.apertechprojects.queryforge.testenv.config;

import io.github.apertechprojects.queryforge.annotation.EnableQueryForge;
import org.springframework.context.annotation.Configuration;

/**
 * Lives outside the repository packages on purpose: @EnableQueryForge must not affect repository scanning.
 */
@Configuration
@EnableQueryForge
public class QueryForgeTestConfiguration {

}
