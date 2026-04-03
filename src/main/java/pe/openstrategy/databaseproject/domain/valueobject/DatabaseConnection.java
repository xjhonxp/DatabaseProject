package pe.openstrategy.databaseproject.domain.valueobject;

import pe.openstrategy.databaseproject.domain.DatabaseType;

/**
 * Represents a database connection configuration.
 */
public record DatabaseConnection(
        String jdbcUrl,
        String username,
        String password,
        DatabaseType dbType,
        String extendedProperty) {

    public DatabaseConnection {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("JDBC URL cannot be null or blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
        if (dbType == null) {
            throw new IllegalArgumentException("Database type cannot be null");
        }
    }
}