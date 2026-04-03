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
 * SQL Server implementation of DDL extraction (supports both SQL Server and
 * Azure SQL).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SqlServerDdlExtractor implements DdlExtractor {

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
        // SQL Server schemas query
        String query = "SELECT name FROM sys.schemas WHERE principal_id != 1 AND name NOT IN ('dbo', 'guest', 'INFORMATION_SCHEMA', 'sys')";
        try (Connection conn = connectionFactory.createConnection(connection)) {
            return jdbcTemplate.queryForList(conn, query, rs -> new Schema(rs.getString("name")));
        } catch (SQLException e) {
            log.error("Failed to extract schemas from SQL Server", e);
            throw new ExtractionFailedException("Failed to extract schemas: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractTables(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    t.name as table_name,
                    c.name as column_name,
                    tp.name as data_type,
                    c.max_length,
                    c.precision,
                    c.scale,
                    c.is_nullable,
                    dc.definition as default_value,
                    ep.value as column_comment
                FROM sys.tables t
                INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
                LEFT JOIN sys.columns c ON t.object_id = c.object_id
                LEFT JOIN sys.types tp ON c.user_type_id = tp.user_type_id
                LEFT JOIN sys.default_constraints dc ON c.default_object_id = dc.object_id
                LEFT JOIN sys.extended_properties ep ON c.object_id = ep.major_id
                    AND c.column_id = ep.minor_id AND ep.class = 1 AND ep.name = 'MS_Description'
                WHERE s.name = ?
                ORDER BY t.name, c.column_id
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
                        Integer size = rs.getObject("max_length", Integer.class);
                        Integer precision = rs.getObject("precision", Integer.class);
                        Integer scale = rs.getObject("scale", Integer.class);
                        boolean nullable = rs.getBoolean("is_nullable");
                        String defaultValue = rs.getString("default_value");
                        String columnComment = rs.getString("column_comment");

                        // Adjust size for nvarchar/varchar (max_length = bytes, divide by 2 for
                        // nvarchar)
                        if (size != null
                                && (dataType.equalsIgnoreCase("nvarchar") || dataType.equalsIgnoreCase("nchar"))) {
                            if (size == -1) {
                                size = null; // MAX
                            } else {
                                size = size / 2; // nvarchar uses 2 bytes per character
                            }
                        } else if (size != null && size == -1) {
                            size = null; // MAX for varchar, varbinary
                        }

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
            log.error("Failed to extract tables from SQL Server schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract tables: " + e.getMessage(), e);
        }
    }

    private DdlStatement createTableDdl(String schemaName, String tableName, List<ColumnInfo> columns) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(schemaName).append(".").append(tableName).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            ddl.append("    ").append(col.name()).append(" ").append(col.dataType());

            // Handle SQL Server-specific type modifiers
            String dataType = col.dataType().toUpperCase();
            if (dataType.contains("CHAR") || dataType.equals("BINARY") || dataType.equals("VARBINARY")) {
                if (col.size() != null) {
                    if (dataType.startsWith("N")) {
                        // nvarchar, nchar: size is number of characters (already adjusted)
                        ddl.append("(").append(col.size() == null ? "MAX" : col.size()).append(")");
                    } else {
                        ddl.append("(").append(col.size() == null ? "MAX" : col.size()).append(")");
                    }
                } else if (dataType.equals("VARCHAR") || dataType.equals("NVARCHAR") ||
                        dataType.equals("VARBINARY")) {
                    ddl.append("(MAX)");
                }
            } else if (dataType.equals("DECIMAL") || dataType.equals("NUMERIC")) {
                if (col.precision() != null && col.scale() != null) {
                    ddl.append("(").append(col.precision()).append(",").append(col.scale()).append(")");
                } else if (col.precision() != null) {
                    ddl.append("(").append(col.precision()).append(")");
                }
            } else if (dataType.equals("FLOAT") || dataType.equals("REAL")) {
                if (col.precision() != null && col.precision() != 53) { // 53 is default for FLOAT
                    ddl.append("(").append(col.precision()).append(")");
                }
            } else if (dataType.equals("DATETIME2") || dataType.equals("TIME") ||
                    dataType.equals("DATETIMEOFFSET")) {
                if (col.scale() != null && col.scale() > 0) {
                    ddl.append("(").append(col.scale()).append(")");
                }
            }

            if (!col.nullable()) {
                ddl.append(" NOT NULL");
            }
            if (col.defaultValue() != null && !col.defaultValue().isEmpty()) {
                ddl.append(" DEFAULT ").append(col.defaultValue());
            }
            if (i < columns.size() - 1) {
                ddl.append(",");
            }
            ddl.append("\n");
        }
        ddl.append(");");

        // Add column comments if any (SQL Server uses extended properties)
        boolean hasComments = false;
        for (ColumnInfo col : columns) {
            if (col.comment() != null && !col.comment().isEmpty()) {
                hasComments = true;
                break;
            }
        }
        if (hasComments) {
            ddl.append("\n\n-- Column comments (using extended properties):\n");
            for (ColumnInfo col : columns) {
                if (col.comment() != null && !col.comment().isEmpty()) {
                    ddl.append("EXEC sp_addextendedproperty ")
                            .append("'MS_Description', '").append(col.comment().replace("'", "''")).append("', ")
                            .append("'SCHEMA', '").append(schemaName).append("', ")
                            .append("'TABLE', '").append(tableName).append("', ")
                            .append("'COLUMN', '").append(col.name()).append("';\n");
                }
            }
        }
        return new DdlStatement(tableName, schemaName, "tables", ddl.toString());
    }

    private DdlStatement createIndexDdl(String schemaName, String tableName, String indexName,
            List<String> columns, boolean isUnique, boolean isPrimary,
            String indexType) {
        StringBuilder ddl = new StringBuilder();
        if (isPrimary) {
            ddl.append("ALTER TABLE ").append(schemaName).append(".").append(tableName)
                    .append(" ADD CONSTRAINT ").append(indexName).append(" PRIMARY KEY ");
            if (indexType != null && indexType.equalsIgnoreCase("CLUSTERED")) {
                ddl.append("CLUSTERED ");
            } else if (indexType != null && indexType.equalsIgnoreCase("NONCLUSTERED")) {
                ddl.append("NONCLUSTERED ");
            }
            ddl.append("(");
        } else if (isUnique) {
            ddl.append("CREATE UNIQUE ");
            if (indexType != null && indexType.equalsIgnoreCase("CLUSTERED")) {
                ddl.append("CLUSTERED ");
            } else if (indexType != null && indexType.equalsIgnoreCase("NONCLUSTERED")) {
                ddl.append("NONCLUSTERED ");
            }
            ddl.append("INDEX ").append(indexName)
                    .append(" ON ").append(schemaName).append(".").append(tableName)
                    .append(" (");
        } else {
            ddl.append("CREATE ");
            if (indexType != null && indexType.equalsIgnoreCase("CLUSTERED")) {
                ddl.append("CLUSTERED ");
            } else if (indexType != null && indexType.equalsIgnoreCase("NONCLUSTERED")) {
                ddl.append("NONCLUSTERED ");
            }
            ddl.append("INDEX ").append(indexName)
                    .append(" ON ").append(schemaName).append(".").append(tableName)
                    .append(" (");
        }
        for (int i = 0; i < columns.size(); i++) {
            ddl.append(columns.get(i));
            if (i < columns.size() - 1) {
                ddl.append(", ");
            }
        }
        ddl.append(");");
        return new DdlStatement(indexName, schemaName, "indexes", ddl.toString());
    }

    @Override
    public List<DdlStatement> extractViews(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    v.name as view_name,
                    m.definition as view_definition,
                    s.name as schema_name
                FROM sys.views v
                INNER JOIN sys.schemas s ON v.schema_id = s.schema_id
                INNER JOIN sys.sql_modules m ON v.object_id = m.object_id
                WHERE s.name = ?
                ORDER BY v.name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String viewName = rs.getString("view_name");
                String viewDefinition = rs.getString("view_definition");
                // Ensure the definition ends with semicolon
                if (viewDefinition != null && !viewDefinition.trim().endsWith(";")) {
                    viewDefinition = viewDefinition.trim() + ";";
                }

                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE VIEW ").append(schema.name()).append(".").append(viewName)
                        .append(" AS\n");
                ddl.append(viewDefinition != null ? viewDefinition : "-- View definition not available");
                return new DdlStatement(viewName, schema.name(), "views", ddl.toString());
            });
        } catch (SQLException e) {
            log.error("Failed to extract views from SQL Server schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract views: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractIndexes(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    i.name as index_name,
                    t.name as table_name,
                    c.name as column_name,
                    i.is_unique,
                    i.is_primary_key,
                    i.type_desc as index_type,
                    ic.key_ordinal as column_order
                FROM sys.indexes i
                INNER JOIN sys.tables t ON i.object_id = t.object_id
                INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
                INNER JOIN sys.index_columns ic ON i.object_id = ic.object_id AND i.index_id = ic.index_id
                INNER JOIN sys.columns c ON ic.object_id = c.object_id AND ic.column_id = c.column_id
                WHERE s.name = ?
                  AND i.type_desc NOT IN ('HEAP', 'XML', 'SPATIAL')
                  AND i.is_hypothetical = 0
                  AND i.is_disabled = 0
                ORDER BY t.name, i.name, ic.key_ordinal
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

                    if (currentIndex == null) {
                        currentIndex = indexName;
                        currentTable = tableName;
                        isUnique = rs.getBoolean("is_unique");
                        isPrimary = rs.getBoolean("is_primary_key");
                        indexType = rs.getString("index_type");
                    } else if (!indexName.equals(currentIndex)) {
                        // Finished processing previous index
                        ddlStatements.add(createIndexDdl(schema.name(), currentTable, currentIndex,
                                columns, isUnique, isPrimary, indexType));
                        columns = new ArrayList<>();
                        currentIndex = indexName;
                        currentTable = tableName;
                        isUnique = rs.getBoolean("is_unique");
                        isPrimary = rs.getBoolean("is_primary_key");
                        indexType = rs.getString("index_type");
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
            log.error("Failed to extract indexes from SQL Server schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract indexes: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractStoredProcedures(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    p.name as procedure_name,
                    m.definition as procedure_definition,
                    s.name as schema_name,
                    p.type_desc as procedure_type
                FROM sys.procedures p
                INNER JOIN sys.schemas s ON p.schema_id = s.schema_id
                LEFT JOIN sys.sql_modules m ON p.object_id = m.object_id
                WHERE s.name = ?
                  AND p.type = 'P'  -- SQL Stored Procedure
                  AND p.is_ms_shipped = 0
                  AND p.name NOT LIKE 'sp_%'  -- Exclude system procedures
                ORDER BY p.name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String procedureName = rs.getString("procedure_name");
                String procedureDefinition = rs.getString("procedure_definition");
                String procedureType = rs.getString("procedure_type");

                // Ensure the definition ends with semicolon
                if (procedureDefinition != null && !procedureDefinition.trim().endsWith(";")) {
                    procedureDefinition = procedureDefinition.trim() + ";";
                }

                // If definition is null, build a basic CREATE PROCEDURE statement
                if (procedureDefinition == null || procedureDefinition.isEmpty()) {
                    StringBuilder ddl = new StringBuilder();
                    ddl.append("CREATE PROCEDURE ").append(schema.name()).append(".").append(procedureName)
                            .append("\n    -- Procedure definition not available\nAS\n    RETURN;");
                    return new DdlStatement(procedureName, schema.name(), "procedures", ddl.toString());
                } else {
                    return new DdlStatement(procedureName, schema.name(), "procedures", procedureDefinition);
                }
            });
        } catch (SQLException e) {
            log.error("Failed to extract stored procedures from SQL Server schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract stored procedures: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractFunctions(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    o.name as function_name,
                    m.definition as function_definition,
                    s.name as schema_name,
                    o.type_desc as function_type
                FROM sys.objects o
                INNER JOIN sys.schemas s ON o.schema_id = s.schema_id
                LEFT JOIN sys.sql_modules m ON o.object_id = m.object_id
                WHERE s.name = ?
                  AND o.type IN ('FN', 'IF', 'TF', 'FS', 'FT')  -- Scalar, inline table-valued, table-valued, CLR scalar, CLR table-valued
                  AND o.is_ms_shipped = 0
                ORDER BY o.name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String functionName = rs.getString("function_name");
                String functionDefinition = rs.getString("function_definition");
                String functionType = rs.getString("function_type");

                // Ensure the definition ends with semicolon
                if (functionDefinition != null && !functionDefinition.trim().endsWith(";")) {
                    functionDefinition = functionDefinition.trim() + ";";
                }

                // If definition is null, build a basic CREATE FUNCTION statement
                if (functionDefinition == null || functionDefinition.isEmpty()) {
                    StringBuilder ddl = new StringBuilder();
                    ddl.append("CREATE FUNCTION ").append(schema.name()).append(".").append(functionName)
                            .append("()\nRETURNS INT\n    -- Function definition not available\nAS\nBEGIN\n    RETURN 0;\nEND;");
                    return new DdlStatement(functionName, schema.name(), "functions", ddl.toString());
                } else {
                    return new DdlStatement(functionName, schema.name(), "functions", functionDefinition);
                }
            });
        } catch (SQLException e) {
            log.error("Failed to extract functions from SQL Server schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract functions: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractSequences(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    s.name as sequence_name,
                    s.start_value,
                    s.increment,
                    s.minimum_value,
                    s.maximum_value,
                    s.is_cycling,
                    s.is_cached,
                    s.cache_size,
                    s.data_type,
                    sch.name as schema_name
                FROM sys.sequences s
                INNER JOIN sys.schemas sch ON s.schema_id = sch.schema_id
                WHERE sch.name = ?
                ORDER BY s.name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String sequenceName = rs.getString("sequence_name");
                Long startValue = rs.getObject("start_value", Long.class);
                Long increment = rs.getObject("increment", Long.class);
                Long minValue = rs.getObject("minimum_value", Long.class);
                Long maxValue = rs.getObject("maximum_value", Long.class);
                Boolean isCycling = rs.getObject("is_cycling", Boolean.class);
                Boolean isCached = rs.getObject("is_cached", Boolean.class);
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
                if (isCached != null && isCached && cacheSize != null && cacheSize > 0) {
                    ddl.append("\n    CACHE ").append(cacheSize);
                } else if (isCached != null && !isCached) {
                    ddl.append("\n    NO CACHE");
                }
                if (isCycling != null && isCycling) {
                    ddl.append("\n    CYCLE");
                } else if (isCycling != null && !isCycling) {
                    ddl.append("\n    NO CYCLE");
                }
                ddl.append(";");
                return new DdlStatement(sequenceName, schema.name(), "sequences", ddl.toString());
            });
        } catch (SQLException e) {
            log.error("Failed to extract sequences from SQL Server schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract sequences: " + e.getMessage(), e);
        }
    }
}