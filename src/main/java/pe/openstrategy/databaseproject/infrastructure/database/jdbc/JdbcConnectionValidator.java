package pe.openstrategy.databaseproject.infrastructure.database.jdbc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.openstrategy.databaseproject.port.out.DatabaseConnectionValidator;
import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;
import pe.openstrategy.databaseproject.infrastructure.jdbc.JdbcConnectionFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * JDBC-based database connection validator using JdbcConnectionFactory.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcConnectionValidator implements DatabaseConnectionValidator {
    
    private final JdbcConnectionFactory connectionFactory;
    
    @Override
    public boolean isValid(DatabaseConnection connection) {
        if (connection == null) {
            log.warn("Connection is null");
            return false;
        }
        
        try (Connection conn = connectionFactory.createConnection(connection)) {
            log.debug("Connection validation successful for {}", connection.jdbcUrl());
            return true;
        } catch (SQLException e) {
            log.warn("Connection validation failed for {}: {}", connection.jdbcUrl(), e.getMessage());
            return false;
        }
    }
}