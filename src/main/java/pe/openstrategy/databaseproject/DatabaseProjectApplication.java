package pe.openstrategy.databaseproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;

/**
 * Main entry point for the DatabaseProject application.
 */
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
// @SpringBootApplication
public class DatabaseProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(DatabaseProjectApplication.class, args);
    }
}