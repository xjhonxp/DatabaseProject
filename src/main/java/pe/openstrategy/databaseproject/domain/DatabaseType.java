package pe.openstrategy.databaseproject.domain;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum DatabaseType {
    POSTGRESQL("org.postgresql.Driver", 5432, "postgresql"),
    ORACLE("oracle.jdbc.OracleDriver", 1521, "oracle"),
    SQL_SERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433, "sqlserver"),
    AZURE_SQL("com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433, "sqlserver"),
    MARIADB("org.mariadb.jdbc.Driver", 3306, "mariadb");

    private final String driverClass;
    private final int defaultPort;
    private final String dialect;

    private static final Map<String, DatabaseType> LOOKUP = Stream.of(values())
        .collect(Collectors.toMap(
            dbType -> dbType.name().toLowerCase(),
            Function.identity()
        ));

    DatabaseType(String driverClass, int defaultPort, String dialect) {
        this.driverClass = driverClass;
        this.defaultPort = defaultPort;
        this.dialect = dialect;
    }

    public String getDriverClass() {
        return driverClass;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    public String getDialect() {
        return dialect;
    }

    public static DatabaseType fromString(String dbType) {
        if (dbType == null) {
            throw new IllegalArgumentException("Database type cannot be null");
        }
        DatabaseType type = LOOKUP.get(dbType.toLowerCase().trim());
        if (type == null) {
            throw new IllegalArgumentException("Unsupported database type: " + dbType);
        }
        return type;
    }
}