package pe.openstrategy.databaseproject.infrastructure.jdbc;

import lombok.extern.slf4j.Slf4j;
import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import org.springframework.stereotype.Component;

/**
 * Sole class responsible for creating JDBC connections.
 * Applies extendedProperty via java.util.Properties.
 * Never logs credentials or extendedProperty values.
 */
@Slf4j
@Component
public class JdbcConnectionFactory {

    /**
     * Creates a JDBC connection using the provided database configuration.
     *
     * @param connection database connection configuration
     * @return a new JDBC Connection
     * @throws SQLException if the connection cannot be established
     */
    public Connection createConnection(DatabaseConnection connection) throws SQLException {
        log.debug("Creating JDBC connection for {}", connection.jdbcUrl());

        Properties props = new Properties();
        props.setProperty("user", connection.username());
        props.setProperty("password", connection.password());

        if (connection.extendedProperty() != null && !connection.extendedProperty().isBlank()) {
            applyExtendedProperty(connection.extendedProperty(), props);
            log.debug("propiedad recuperada : {}", props.get("oracle.jdbc.proxyClientName"));
        }

        return DriverManager.getConnection(connection.jdbcUrl(), props);
    }

    void applyExtendedProperty(String extendedProperty, Properties props) {
        // Split by '=', limit 2 to handle values that may contain '='
        String[] parts = extendedProperty.split("=", 2);
        if (parts.length != 2) {
            log.warn("Ignoring malformed extendedProperty (expected key=value format)");
            return;
        }
        String key = parts[0].trim();
        String value = parts[1].trim();
        if (key.isEmpty()) {
            log.warn("Ignoring extendedProperty with empty key");
            return;
        }
        props.setProperty(key, value);
        log.debug("Applied extended property key '{}' (value not logged)", key);
    }
}