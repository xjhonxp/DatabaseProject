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

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Oracle implementation of DDL extraction.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OracleDdlExtractor implements DdlExtractor {

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

    private boolean isSystemSchema(String schemaName) {
        String[] systemSchemas = {
                "SYS", "SYSTEM", "OUTLN", "DBSNMP", "APPQOSSYS", "ORACLE_OCM",
                "DIP", "TSMSYS", "WMSYS", "EXFSYS", "CTXSYS", "XDB", "ANONYMOUS"
        };
        for (String system : systemSchemas) {
            if (system.equalsIgnoreCase(schemaName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Schema> extractSchemas(DatabaseConnection connection) {
        // Oracle uses "users" rather than schemas in the traditional sense
        // For simplicity, we treat Oracle schemas as users with objects
        String query = "SELECT username FROM all_users ORDER BY username";
        try (Connection conn = connectionFactory.createConnection(connection)) {
            return jdbcTemplate.queryForList(conn, query, rs -> {
                String schemaName = rs.getString("username");
                if (!isSystemSchema(schemaName)) {
                    return new Schema(schemaName);
                }
                return null;
            }).stream().filter(s -> s != null).toList();
        } catch (SQLException e) {
            log.error("Failed to extract schemas from Oracle", e);
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
                    c.data_length,
                    c.data_precision,
                    c.data_scale,
                    c.nullable,
                    c.data_default,
                    cc.comments as column_comment
                FROM all_tables t
                LEFT JOIN all_tab_columns c ON t.owner = c.owner AND t.table_name = c.table_name
                LEFT JOIN all_col_comments cc ON t.owner = cc.owner AND t.table_name = cc.table_name AND c.column_name = cc.column_name
                WHERE t.owner = ?
                  AND t.table_name NOT LIKE 'BIN$%'  -- Exclude recycled tables
                ORDER BY t.table_name, c.column_id
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            log.debug("Ingresando a extraccion de tablas");
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
                        Integer size = rs.getObject("data_length", Integer.class);
                        Integer precision = rs.getObject("data_precision", Integer.class);
                        Integer scale = rs.getObject("data_scale", Integer.class);
                        boolean nullable = "Y".equals(rs.getString("nullable"));
                        String defaultValue = rs.getString("data_default");
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
            log.error("Failed to extract tables from Oracle schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract tables: " + e.getMessage(), e);
        }
    }

    private DdlStatement createTableDdl(String schemaName, String tableName, List<ColumnInfo> columns) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(schemaName).append(".").append(tableName).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            ddl.append("    ").append(col.name()).append(" ").append(col.dataType());

            // Handle Oracle-specific type modifiers
            String dataType = col.dataType().toUpperCase();
            if (dataType.contains("VARCHAR") || dataType.equals("CHAR") || dataType.equals("RAW")) {
                if (col.size() != null) {
                    ddl.append("(").append(col.size()).append(")");
                }
            } else if (dataType.equals("NUMBER") || dataType.equals("FLOAT")) {
                if (col.precision() != null && col.scale() != null && col.scale() != 0) {
                    ddl.append("(").append(col.precision()).append(",").append(col.scale()).append(")");
                } else if (col.precision() != null) {
                    ddl.append("(").append(col.precision()).append(")");
                } else if (col.size() != null) {
                    // Some Oracle versions use data_length for NUMBER without precision/scale
                    ddl.append("(").append(col.size()).append(")");
                }
            } else if (dataType.equals("TIMESTAMP") || dataType.equals("INTERVAL")) {
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

        // Add column comments if any
        boolean hasComments = false;
        for (ColumnInfo col : columns) {
            if (col.comment() != null && !col.comment().isEmpty()) {
                hasComments = true;
                break;
            }
        }
        if (hasComments) {
            ddl.append("\n\n-- Column comments:\n");
            for (ColumnInfo col : columns) {
                if (col.comment() != null && !col.comment().isEmpty()) {
                    ddl.append("COMMENT ON COLUMN ").append(schemaName).append(".").append(tableName)
                            .append(".").append(col.name()).append(" IS '").append(col.comment()).append("';\n");
                }
            }
        }
        return new DdlStatement(tableName, schemaName, "tables", ddl.toString());
    }

    @Override
    public List<DdlStatement> extractViews(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    v.view_name,
                    dbms_metadata.get_ddl('VIEW', v.view_name, v.owner) as view_ddl,
                    v.owner
                FROM all_views v
                WHERE v.owner = ?
                ORDER BY v.view_name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String viewName = rs.getString("view_name");
                String viewDdl = rs.getString("view_ddl");
                String owner = rs.getString("owner");

                // Ensure DDL is not null and ends with semicolon
                if (viewDdl != null && !viewDdl.trim().endsWith(";")) {
                    viewDdl = viewDdl.trim() + ";";
                }

                // If DDL is still null, build a basic CREATE VIEW statement
                if (viewDdl == null || viewDdl.isEmpty()) {
                    StringBuilder ddl = new StringBuilder();
                    ddl.append("CREATE VIEW ").append(schema.name()).append(".").append(viewName)
                            .append(" AS\n    -- View definition not available;");
                    return new DdlStatement(viewName, schema.name(), "views", ddl.toString());
                } else {
                    return new DdlStatement(viewName, schema.name(), "views", viewDdl);
                }
            });
        } catch (SQLException e) {
            log.error("Failed to extract views from Oracle schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract views: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractIndexes(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    i.index_name,
                    i.table_name,
                    i.uniqueness,
                    i.index_type,
                    c.column_name,
                    c.column_position
                FROM all_indexes i
                LEFT JOIN all_ind_columns c ON i.index_name = c.index_name AND i.table_owner = c.table_owner AND i.table_name = c.table_name
                WHERE i.table_owner = ?
                  AND i.dropped = 'NO'
                  AND i.index_type NOT LIKE '%LOB%'  -- Exclude LOB indexes
                ORDER BY i.table_name, i.index_name, c.column_position
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.query(conn, query, params, rs -> {
                List<DdlStatement> ddlStatements = new ArrayList<>();
                String currentIndex = null;
                String currentTable = null;
                List<String> columns = new ArrayList<>();
                boolean isUnique = false;
                String indexType = null;

                while (rs.next()) {
                    String indexName = rs.getString("index_name");
                    String tableName = rs.getString("table_name");
                    String columnName = rs.getString("column_name");

                    if (currentIndex == null) {
                        currentIndex = indexName;
                        currentTable = tableName;
                        isUnique = "UNIQUE".equals(rs.getString("uniqueness"));
                        indexType = rs.getString("index_type");
                    } else if (!indexName.equals(currentIndex)) {
                        // Finished processing previous index
                        ddlStatements.add(createIndexDdl(schema.name(), currentTable, currentIndex,
                                columns, isUnique, indexType));
                        columns = new ArrayList<>();
                        currentIndex = indexName;
                        currentTable = tableName;
                        isUnique = "UNIQUE".equals(rs.getString("uniqueness"));
                        indexType = rs.getString("index_type");
                    }

                    if (columnName != null) {
                        columns.add(columnName);
                    }
                }

                // Add the last index
                if (currentIndex != null) {
                    ddlStatements.add(createIndexDdl(schema.name(), currentTable, currentIndex,
                            columns, isUnique, indexType));
                }

                log.debug("Extracted {} indexes from schema {}", ddlStatements.size(), schema.name());
                return ddlStatements;
            });
        } catch (SQLException e) {
            log.error("Failed to extract indexes from Oracle schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract indexes: " + e.getMessage(), e);
        }
    }

    private DdlStatement createIndexDdl(String schemaName, String tableName, String indexName,
            List<String> columns, boolean isUnique, String indexType) {
        StringBuilder ddl = new StringBuilder();
        if (isUnique) {
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
        // Add index type if specified and not default (NORMAL)
        if (indexType != null && !indexType.equalsIgnoreCase("NORMAL")) {
            ddl.append(" ").append(indexType);
        }
        ddl.append(";");
        return new DdlStatement(indexName, schemaName, "indexes", ddl.toString());
    }

    @Override
    public List<DdlStatement> extractStoredProcedures(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    p.object_name as procedure_name,
                    dbms_metadata.get_ddl('PROCEDURE', p.object_name, p.owner) as procedure_ddl,
                    p.owner
                FROM all_procedures p
                WHERE p.owner = ?
                  AND p.object_type = 'PROCEDURE'
                ORDER BY p.object_name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String procedureName = rs.getString("procedure_name");
                String procedureDdl = rs.getString("procedure_ddl");
                String owner = rs.getString("owner");

                // Ensure DDL ends with semicolon
                if (procedureDdl != null && !procedureDdl.trim().endsWith(";")) {
                    procedureDdl = procedureDdl.trim() + ";";
                }

                if (procedureDdl != null && !procedureDdl.isEmpty()) {
                    return new DdlStatement(procedureName, schema.name(), "procedures", procedureDdl);
                } else {
                    StringBuilder ddl = new StringBuilder();
                    ddl.append("CREATE OR REPLACE PROCEDURE ").append(schema.name()).append(".").append(procedureName)
                            .append(" IS\nBEGIN\n    -- Procedure definition not available\nEND;");
                    return new DdlStatement(procedureName, schema.name(), "procedures", ddl.toString());
                }
            });
        } catch (SQLException e) {
            log.error("Failed to extract stored procedures from Oracle schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract stored procedures: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractFunctions(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    p.object_name as function_name,
                    dbms_metadata.get_ddl('FUNCTION', p.object_name, p.owner) as function_ddl,
                    p.owner
                FROM all_procedures p
                WHERE p.owner = ?
                  AND p.object_type = 'FUNCTION'
                ORDER BY p.object_name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String functionName = rs.getString("function_name");
                String functionDdl = rs.getString("function_ddl");
                String owner = rs.getString("owner");

                // Ensure DDL ends with semicolon
                if (functionDdl != null && !functionDdl.trim().endsWith(";")) {
                    functionDdl = functionDdl.trim() + ";";
                }

                if (functionDdl != null && !functionDdl.isEmpty()) {
                    return new DdlStatement(functionName, schema.name(), "functions", functionDdl);
                } else {
                    StringBuilder ddl = new StringBuilder();
                    ddl.append("CREATE OR REPLACE FUNCTION ").append(schema.name()).append(".").append(functionName)
                            .append(" RETURN VARCHAR2 IS\nBEGIN\n    RETURN NULL;\nEND;");
                    return new DdlStatement(functionName, schema.name(), "functions", ddl.toString());
                }
            });
        } catch (SQLException e) {
            log.error("Failed to extract functions from Oracle schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract functions: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractSequences(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    s.sequence_name,
                    s.min_value,
                    s.max_value,
                    s.increment_by,
                    s.cycle_flag,
                    s.order_flag,
                    s.cache_size,
                    s.last_number
                FROM all_sequences s
                WHERE s.sequence_owner = ?
                ORDER BY s.sequence_name
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String sequenceName = rs.getString("sequence_name");
                BigInteger minValue = rs.getObject("min_value", BigInteger.class);
                BigInteger maxValue = rs.getObject("max_value", BigInteger.class);
                Long incrementBy = rs.getObject("increment_by", Long.class);
                String cycleFlag = rs.getString("cycle_flag");
                String orderFlag = rs.getString("order_flag");
                Long cacheSize = rs.getObject("cache_size", Long.class);
                Long lastNumber = rs.getObject("last_number", Long.class);

                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE SEQUENCE ").append(schema.name()).append(".").append(sequenceName);
                if (minValue != null) {
                    ddl.append("\n    MINVALUE ").append(minValue);
                }
                if (maxValue != null) {
                    ddl.append("\n    MAXVALUE ").append(maxValue);
                }
                if (incrementBy != null) {
                    ddl.append("\n    INCREMENT BY ").append(incrementBy);
                }
                if (cacheSize != null) {
                    ddl.append("\n    CACHE ").append(cacheSize);
                }
                if ("Y".equals(cycleFlag)) {
                    ddl.append("\n    CYCLE");
                } else {
                    ddl.append("\n    NOCYCLE");
                }
                if ("Y".equals(orderFlag)) {
                    ddl.append("\n    ORDER");
                } else {
                    ddl.append("\n    NOORDER");
                }
                // START WITH is not directly available; we can use LAST_NUMBER as approximation
                if (lastNumber != null) {
                    ddl.append("\n    START WITH ").append(lastNumber);
                }
                ddl.append(";");
                return new DdlStatement(sequenceName, schema.name(), "sequences", ddl.toString());
            });
        } catch (SQLException e) {
            log.error("Failed to extract sequences from Oracle schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract sequences: " + e.getMessage(), e);
        }
    }
}