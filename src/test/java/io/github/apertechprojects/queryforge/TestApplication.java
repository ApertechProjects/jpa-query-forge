package io.github.apertechprojects.queryforge;

import io.github.apertechprojects.queryforge.annotation.EnableQueryForge;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableQueryForge
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

}
