package pe.openstrategy.databaseproject.infrastructure.extractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.openstrategy.databaseproject.application.exception.ExtractionFailedException;
import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;
import pe.openstrategy.databaseproject.domain.valueobject.DdlStatement;
import pe.openstrategy.databaseproject.domain.valueobject.Schema;
import pe.openstrategy.databaseproject.infrastructure.jdbc.JdbcConnectionFactory;
import pe.openstrategy.databaseproject.infrastructure.jdbc.JdbcTemplate;
import pe.openstrategy.databaseproject.port.out.DdlExtractor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * MariaDB implementation of DDL extraction.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MariaDbDdlExtractor implements DdlExtractor {

    private final JdbcConnectionFactory connectionFactory;
    private final JdbcTemplate jdbcTemplate;

    private record ColumnInfo(
            String name,
            String dataType,
            Integer size,
            Integer precision,
            Integer scale,
            boolean nullable,
            String defaultValue,
            String comment) {
    }

    @Override
    public List<Schema> extractSchemas(DatabaseConnection connection) {
        // MariaDB schemas (databases) query
        String query = "SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT IN ('information_schema', 'mysql', 'performance_schema', 'sys')";
        try (Connection conn = connectionFactory.createConnection(connection)) {
            return jdbcTemplate.queryForList(conn, query, rs -> new Schema(rs.getString("schema_name")));
        } catch (SQLException e) {
            log.error("Failed to extract schemas from MariaDB", e);
            throw new ExtractionFailedException("Failed to extract schemas: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractTables(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    t.table_name,
                    c.column_name,
                    c.data_type,
                    c.character_maximum_length,
                    c.numeric_precision,
                    c.numeric_scale,
                    c.is_nullable,
                    c.column_default,
                    c.column_comment
                FROM information_schema.tables t
                LEFT JOIN information_schema.columns c ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                WHERE t.table_schema = ?
                  AND t.table_type = 'BASE TABLE'
                ORDER BY t.table_name, c.ordinal_position
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.query(conn, query, params, rs -> {
                List<DdlStatement> ddlStatements = new ArrayList<>();
                String currentTable = null;
                List<ColumnInfo> columns = new ArrayList<>();

                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    if (currentTable == null) {
                        currentTable = tableName;
                    } else if (!tableName.equals(currentTable)) {
                        // Finished processing previous table
                        ddlStatements.add(createTableDdl(schema.name(), currentTable, columns));
                        columns = new ArrayList<>();
                        currentTable = tableName;
                    }

                    String columnName = rs.getString("column_name");
                    if (columnName != null) {
                        String dataType = rs.getString("data_type");
                        Integer size = rs.getObject("character_maximum_length", Integer.class);
                        Integer precision = rs.getObject("numeric_precision", Integer.class);
                        Integer scale = rs.getObject("numeric_scale", Integer.class);
                        boolean nullable = "YES".equals(rs.getString("is_nullable"));
                        String defaultValue = rs.getString("column_default");
                        String columnComment = rs.getString("column_comment");

                        columns.add(new ColumnInfo(columnName, dataType, size, precision, scale, nullable, defaultValue,
                                columnComment));
                    }
                }

                // Add the last table
                if (currentTable != null) {
                    ddlStatements.add(createTableDdl(schema.name(), currentTable, columns));
                }

                log.debug("Extracted {} tables from schema {}", ddlStatements.size(), schema.name());
                return ddlStatements;
            });
        } catch (SQLException e) {
            log.error("Failed to extract tables from MariaDB schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract tables: " + e.getMessage(), e);
        }
    }

    private DdlStatement createTableDdl(String schemaName, String tableName, List<ColumnInfo> columns) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(schemaName).append(".").append(tableName).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            ddl.append("    ").append(col.name()).append(" ").append(col.dataType());

            // Handle MariaDB/MySQL type modifiers
            String dataType = col.dataType().toUpperCase();
            if (dataType.contains("CHAR") || dataType.equals("BINARY") || dataType.equals("VARBINARY") ||
                    dataType.contains("TEXT") || dataType.contains("BLOB")) {
                if (col.size() != null) {
                    ddl.append("(").append(col.size()).append(")");
                }
            } else if (dataType.equals("DECIMAL") || dataType.equals("NUMERIC") ||
                    dataType.equals("FLOAT") || dataType.equals("DOUBLE")) {
                if (col.precision() != null && col.scale() != null) {
                    ddl.append("(").append(col.precision()).append(",").append(col.scale()).append(")");
                } else if (col.precision() != null) {
                    ddl.append("(").append(col.precision()).append(")");
                }
            } else if (dataType.equals("BIT")) {
                if (col.size() != null && col.size() > 1) {
                    ddl.append("(").append(col.size()).append(")");
                }
            } else if (dataType.equals("ENUM") || dataType.equals("SET")) {
                // ENUM and SET values are handled differently, skip size
            }

            if (!col.nullable()) {
                ddl.append(" NOT NULL");
            }
            if (col.defaultValue() != null && !col.defaultValue().isEmpty()) {
                ddl.append(" DEFAULT ").append(col.defaultValue());
            }
            if (col.comment() != null && !col.comment().isEmpty()) {
                ddl.append(" COMMENT '").append(col.comment().replace("'", "''")).append("'");
            }
            if (i < columns.size() - 1) {
                ddl.append(",");
            }
            ddl.append("\n");
        }
        ddl.append(")");

        // Add table-level options (simplified)
        ddl.append(" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        return new DdlStatement(tableName, schemaName, "tables", ddl.toString());
    }

    private DdlStatement createIndexDdl(String schemaName, String tableName, String indexName,
            List<String> columns, boolean isUnique, boolean isPrimary,
            String indexType) {
        StringBuilder ddl = new StringBuilder();
        if (isPrimary) {
            ddl.append("ALTER TABLE ").append(schemaName).append(".").append(tableName)
                    .append(" ADD PRIMARY KEY (");
        } else if (isUnique) {
            ddl.append("CREATE UNIQUE INDEX ").append(indexName)
                    .append(" ON ").append(schemaName).append(".").append(tableName)
                    .append(" (");
        } else {
            ddl.append("CREATE INDEX ").append(indexName)
                    .append(" ON ").append(schemaName).append(".").append(tableName)
                    .append(" (");
        }
        for (int i = 0; i < columns.size(); i++) {
            ddl.append(columns.get(i));
            if (i < columns.size() - 1) {
                ddl.append(", ");
            }
        }
        ddl.append(")");
        // Add index type if specified and not default (BTREE)
        if (indexType != null && !indexType.equalsIgnoreCase("BTREE")) {
            ddl.append(" USING ").append(indexType.toUpperCase());
        }
        ddl.append(";");
        return new DdlStatement(indexName, schemaName, "indexes", ddl.toString());
    }

    @Override
    public List<DdlStatement> extractViews(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    table_name as view_name,
                    view_definition,
                    check_option,
                    is_updatable,
                    definer,
                    security_type
                FROM information_schema.views
                WHERE table_schema = ?
                ORDER BY table_name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String viewName = rs.getString("view_name");
                String viewDefinition = rs.getString("view_definition");
                String checkOption = rs.getString("check_option");
                String isUpdatable = rs.getString("is_updatable");
                String definer = rs.getString("definer");
                String securityType = rs.getString("security_type");

                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE ");
                // Security type: DEFINER, INVOKER
                if (securityType != null && securityType.equalsIgnoreCase("DEFINER")) {
                    ddl.append("DEFINER=").append(definer).append(" ");
                }
                ddl.append("VIEW ").append(schema.name()).append(".").append(viewName)
                        .append(" AS\n");
                ddl.append(viewDefinition != null ? viewDefinition : "-- View definition not available");
                if (checkOption != null && checkOption.equalsIgnoreCase("CASCADED")) {
                    ddl.append("\nWITH CASCADED CHECK OPTION");
                } else if (checkOption != null && checkOption.equalsIgnoreCase("LOCAL")) {
                    ddl.append("\nWITH LOCAL CHECK OPTION");
                }
                ddl.append(";");
                return new DdlStatement(viewName, schema.name(), "views", ddl.toString());
            });
        } catch (SQLException e) {
            log.error("Failed to extract views from MariaDB schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract views: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractIndexes(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    index_name,
                    table_name,
                    column_name,
                    non_unique,
                    index_type,
                    seq_in_index
                FROM information_schema.statistics
                WHERE table_schema = ?
                ORDER BY table_name, index_name, seq_in_index
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.query(conn, query, params, rs -> {
                List<DdlStatement> ddlStatements = new ArrayList<>();
                String currentIndex = null;
                String currentTable = null;
                List<String> columns = new ArrayList<>();
                boolean isUnique = false;
                boolean isPrimary = false;
                String indexType = null;

                while (rs.next()) {
                    String indexName = rs.getString("index_name");
                    String tableName = rs.getString("table_name");
                    String columnName = rs.getString("column_name");
                    boolean nonUnique = rs.getBoolean("non_unique");
                    String type = rs.getString("index_type");
                    // PRIMARY key has index_name = 'PRIMARY'
                    boolean primary = "PRIMARY".equals(indexName);

                    if (currentIndex == null) {
                        currentIndex = indexName;
                        currentTable = tableName;
                        isUnique = !nonUnique && !primary;
                        isPrimary = primary;
                        indexType = type;
                    } else if (!indexName.equals(currentIndex)) {
                        // Finished processing previous index
                        ddlStatements.add(createIndexDdl(schema.name(), currentTable, currentIndex,
                                columns, isUnique, isPrimary, indexType));
                        columns = new ArrayList<>();
                        currentIndex = indexName;
                        currentTable = tableName;
                        isUnique = !nonUnique && !primary;
                        isPrimary = primary;
                        indexType = type;
                    }

                    if (columnName != null) {
                        columns.add(columnName);
                    }
                }

                // Add the last index
                if (currentIndex != null) {
                    ddlStatements.add(createIndexDdl(schema.name(), currentTable, currentIndex,
                            columns, isUnique, isPrimary, indexType));
                }

                log.debug("Extracted {} indexes from schema {}", ddlStatements.size(), schema.name());
                return ddlStatements;
            });
        } catch (SQLException e) {
            log.error("Failed to extract indexes from MariaDB schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract indexes: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractStoredProcedures(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    routine_name,
                    routine_definition,
                    routine_body,
                    routine_comment,
                    definer,
                    security_type,
                    sql_data_access,
                    is_deterministic
                FROM information_schema.routines
                WHERE routine_schema = ?
                  AND routine_type = 'PROCEDURE'
                  AND routine_name NOT LIKE 'mysql.%'
                ORDER BY routine_name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String procedureName = rs.getString("routine_name");
                String routineDefinition = rs.getString("routine_definition");
                String routineBody = rs.getString("routine_body");
                String routineComment = rs.getString("routine_comment");
                String definer = rs.getString("definer");
                String securityType = rs.getString("security_type");
                String sqlDataAccess = rs.getString("sql_data_access");
                String isDeterministic = rs.getString("is_deterministic");

                // Build CREATE PROCEDURE statement
                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE ");
                // Add definer if present
                if (definer != null && !definer.isEmpty()) {
                    ddl.append("DEFINER=").append(definer).append(" ");
                }
                ddl.append("PROCEDURE ").append(schema.name()).append(".").append(procedureName);
                // Note: parameter list not available in information_schema.routines
                // We'd need to query information_schema.parameters for complete signature
                // For now, assume no parameters
                ddl.append("()\n");
                // Add characteristics
                if (isDeterministic != null && isDeterministic.equals("YES")) {
                    ddl.append("    DETERMINISTIC\n");
                }
                if (sqlDataAccess != null && !sqlDataAccess.equals("CONTAINS_SQL")) {
                    ddl.append("    ").append(sqlDataAccess).append(" SQL\n");
                }
                if (securityType != null) {
                    ddl.append("    SQL SECURITY ").append(securityType).append("\n");
                }
                if (routineComment != null && !routineComment.isEmpty()) {
                    ddl.append("    COMMENT '").append(routineComment.replace("'", "''")).append("'\n");
                }
                ddl.append("BEGIN\n");
                if (routineDefinition != null && !routineDefinition.isEmpty()) {
                    ddl.append(routineDefinition);
                } else {
                    ddl.append("    -- Procedure body not available");
                }
                ddl.append("\nEND;");
                return new DdlStatement(procedureName, schema.name(), "procedures", ddl.toString());
            });
        } catch (SQLException e) {
            log.error("Failed to extract stored procedures from MariaDB schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract stored procedures: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractFunctions(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    routine_name,
                    routine_definition,
                    routine_body,
                    routine_comment,
                    definer,
                    security_type,
                    sql_data_access,
                    is_deterministic,
                    data_type as return_type,
                    dtd_identifier as return_type_definition
                FROM information_schema.routines
                WHERE routine_schema = ?
                  AND routine_type = 'FUNCTION'
                  AND routine_name NOT LIKE 'mysql.%'
                ORDER BY routine_name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String functionName = rs.getString("routine_name");
                String routineDefinition = rs.getString("routine_definition");
                String routineBody = rs.getString("routine_body");
                String routineComment = rs.getString("routine_comment");
                String definer = rs.getString("definer");
                String securityType = rs.getString("security_type");
                String sqlDataAccess = rs.getString("sql_data_access");
                String isDeterministic = rs.getString("is_deterministic");
                String returnType = rs.getString("return_type");
                String returnTypeDefinition = rs.getString("return_type_definition");

                // Build CREATE FUNCTION statement
                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE ");
                // Add definer if present
                if (definer != null && !definer.isEmpty()) {
                    ddl.append("DEFINER=").append(definer).append(" ");
                }
                ddl.append("FUNCTION ").append(schema.name()).append(".").append(functionName);
                // Note: parameter list not available in information_schema.routines
                // We'd need to query information_schema.parameters for complete signature
                // For now, assume no parameters
                ddl.append("()\n");
                ddl.append("    RETURNS ").append(returnTypeDefinition != null ? returnTypeDefinition
                        : (returnType != null ? returnType : "VARCHAR(255)")).append("\n");
                // Add characteristics
                if (isDeterministic != null && isDeterministic.equals("YES")) {
                    ddl.append("    DETERMINISTIC\n");
                }
                if (sqlDataAccess != null && !sqlDataAccess.equals("CONTAINS_SQL")) {
                    ddl.append("    ").append(sqlDataAccess).append(" SQL\n");
                }
                if (securityType != null) {
                    ddl.append("    SQL SECURITY ").append(securityType).append("\n");
                }
                if (routineComment != null && !routineComment.isEmpty()) {
                    ddl.append("    COMMENT '").append(routineComment.replace("'", "''")).append("'\n");
                }
                ddl.append("BEGIN\n");
                if (routineDefinition != null && !routineDefinition.isEmpty()) {
                    ddl.append(routineDefinition);
                } else {
                    ddl.append("    -- Function body not available");
                }
                ddl.append("\nEND;");
                return new DdlStatement(functionName, schema.name(), "functions", ddl.toString());
            });
        } catch (SQLException e) {
            log.error("Failed to extract functions from MariaDB schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract functions: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractSequences(Schema schema, DatabaseConnection connection) {
        // Check if sequences exist in information_schema (MariaDB 10.3+)
        String query = """
                SELECT
                    sequence_name,
                    start_value,
                    minimum_value,
                    maximum_value,
                    increment,
                    cycle_option,
                    cache_size,
                    data_type
                FROM information_schema.sequences
                WHERE sequence_schema = ?
                ORDER BY sequence_name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String sequenceName = rs.getString("sequence_name");
                Long startValue = rs.getObject("start_value", Long.class);
                Long minValue = rs.getObject("minimum_value", Long.class);
                Long maxValue = rs.getObject("maximum_value", Long.class);
                Long increment = rs.getObject("increment", Long.class);
                String cycleOption = rs.getString("cycle_option");
                Long cacheSize = rs.getObject("cache_size", Long.class);
                String dataType = rs.getString("data_type");

                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE SEQUENCE ").append(schema.name()).append(".").append(sequenceName);
                if (dataType != null && !dataType.isEmpty()) {
                    ddl.append("\n    AS ").append(dataType);
                }
                if (startValue != null) {
                    ddl.append("\n    START WITH ").append(startValue);
                }
                if (increment != null) {
                    ddl.append("\n    INCREMENT BY ").append(increment);
                }
                if (minValue != null) {
                    ddl.append("\n    MINVALUE ").append(minValue);
                }
                if (maxValue != null) {
                    ddl.append("\n    MAXVALUE ").append(maxValue);
                }
                if (cacheSize != null && cacheSize > 0) {
                    ddl.append("\n    CACHE ").append(cacheSize);
                } else if (cacheSize != null && cacheSize == 0) {
                    ddl.append("\n    NOCACHE");
                }
                if (cycleOption != null && cycleOption.equals("YES")) {
                    ddl.append("\n    CYCLE");
                } else if (cycleOption != null && cycleOption.equals("NO")) {
                    ddl.append("\n    NOCYCLE");
                }
                ddl.append(";");
                return new DdlStatement(sequenceName, schema.name(), "sequences", ddl.toString());
            });
        } catch (SQLException e) {
            // If the table doesn't exist (MariaDB <10.3), return empty list
            log.debug("Sequences table not available in MariaDB version, skipping");
            return List.of();
        }
    }
}