package pe.openstrategy.databaseproject.infrastructure.database.jdbc;

import lombok.extern.slf4j.Slf4j;
import pe.openstrategy.databaseproject.domain.DatabaseType;
import pe.openstrategy.databaseproject.domain.exception.UnsupportedDatabaseException;

import java.util.Map;

/**
 * Builds JDBC URLs for different database engines using DatabaseType enum.
 */
@Slf4j
public class JdbcUrlBuilder {
    
    private static final Map<DatabaseType, String> JDBC_URL_TEMPLATES = Map.of(
        DatabaseType.POSTGRESQL, "jdbc:postgresql://%s:%d/%s",
        DatabaseType.ORACLE,     "jdbc:oracle:thin:@%s:%d:%s",
        DatabaseType.SQL_SERVER, "jdbc:sqlserver://%s:%d;databaseName=%s",
        DatabaseType.AZURE_SQL,  "jdbc:sqlserver://%s:%d;databaseName=%s",
        DatabaseType.MARIADB,    "jdbc:mariadb://%s:%d/%s"
    );
    
    /**
     * Builds a JDBC URL for the given database type and connection parameters.
     * 
     * @param databaseType database type enum
     * @param host database host
     * @param port database port
     * @param database database name
     * @return JDBC URL string
     * @throws UnsupportedDatabaseException if databaseType is not supported
     */
    public static String buildUrl(DatabaseType databaseType, String host, int port, String database) {
        String template = JDBC_URL_TEMPLATES.get(databaseType);
        if (template == null) {
            throw new UnsupportedDatabaseException("No URL template for database type: " + databaseType);
        }
        
        String url = String.format(template, host, port, database);
        log.debug("Built JDBC URL for {}: {}", databaseType, maskUrl(url));
        return url;
    }
    
    /**
     * Builds a JDBC URL for the given database type string and connection parameters.
     * Convenience method that converts string to DatabaseType.
     * 
     * @param dbType database type string (case-insensitive)
     * @param host database host
     * @param port database port
     * @param database database name
     * @return JDBC URL string
     * @throws UnsupportedDatabaseException if dbType is not supported
     */
    public static String buildUrl(String dbType, String host, int port, String database) {
        DatabaseType databaseType = DatabaseType.fromString(dbType);
        return buildUrl(databaseType, host, port, database);
    }
    
    /**
     * Masks sensitive parts of JDBC URL for logging.
     */
    private static String maskUrl(String url) {
        // Simple masking: replace password if present
        return url.replaceAll("password=[^&;]+", "password=***");
    }
    
    /**
     * Returns the default JDBC driver class name for the given database type.
     */
    public static String getDefaultDriverClass(DatabaseType databaseType) {
        return databaseType.getDriverClass();
    }
    
    /**
     * Returns the default JDBC driver class name for the given database type string.
     * Convenience method that converts string to DatabaseType.
     */
    public static String getDefaultDriverClass(String dbType) {
        DatabaseType databaseType = DatabaseType.fromString(dbType);
        return databaseType.getDriverClass();
    }
    
    /**
     * Checks if the database type string is supported.
     */
    public static boolean isSupported(String dbType) {
        try {
            DatabaseType.fromString(dbType);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}