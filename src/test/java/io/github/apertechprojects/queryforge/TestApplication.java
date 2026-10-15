package io.github.apertechprojects.queryforge;

import io.github.apertechprojects.queryforge.annotation.EnableQueryForge;
import io.github.apertechprojects.queryforge.annotation.EnableQueryForgeWebArgumentResolvers;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableQueryForge
@EnableQueryForgeWebArgumentResolvers
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

}
