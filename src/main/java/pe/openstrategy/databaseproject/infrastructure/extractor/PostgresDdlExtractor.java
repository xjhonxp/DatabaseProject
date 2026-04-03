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
 * PostgreSQL implementation of DDL extraction.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PostgresDdlExtractor implements DdlExtractor {

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
        String query = "SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast')";
        try (Connection conn = connectionFactory.createConnection(connection)) {
            return jdbcTemplate.queryForList(conn, query, rs -> new Schema(rs.getString("schema_name")));
        } catch (SQLException e) {
            log.error("Failed to extract schemas from PostgreSQL", e);
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
                    pgd.description as column_comment
                FROM information_schema.tables t
                LEFT JOIN information_schema.columns c ON t.table_schema = c.table_schema AND t.table_name = c.table_name
                LEFT JOIN pg_catalog.pg_statio_all_tables st ON t.table_schema = st.schemaname AND t.table_name = st.relname
                LEFT JOIN pg_catalog.pg_description pgd ON pgd.objoid = st.relid AND pgd.objsubid = c.ordinal_position
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
            log.error("Failed to extract tables from PostgreSQL schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract tables: " + e.getMessage(), e);
        }
    }

    private DdlStatement createTableDdl(String schemaName, String tableName, List<ColumnInfo> columns) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE ").append(schemaName).append(".").append(tableName).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            ddl.append("    ").append(col.name()).append(" ").append(col.dataType());

            // Handle PostgreSQL-specific type modifiers
            String dataType = col.dataType().toUpperCase();
            if (dataType.contains("CHAR") || dataType.equals("BIT") || dataType.equals("VARBIT") ||
                    dataType.contains("VARCHAR") || dataType.equals("CHARACTER")) {
                if (col.size() != null && col.size() > 0) {
                    ddl.append("(").append(col.size()).append(")");
                }
            } else if (dataType.equals("NUMERIC") || dataType.equals("DECIMAL")) {
                if (col.precision() != null && col.scale() != null) {
                    ddl.append("(").append(col.precision()).append(",").append(col.scale()).append(")");
                } else if (col.precision() != null) {
                    ddl.append("(").append(col.precision()).append(")");
                }
            } else if (dataType.equals("TIME") || dataType.equals("TIMESTAMP") ||
                    dataType.equals("INTERVAL")) {
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

        // Add column comments if any (PostgreSQL uses COMMENT ON COLUMN)
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
                    v.viewname as view_name,
                    pg_get_viewdef(c.oid, true) as view_definition,
                    v.viewowner as owner,
                    v.schemaname as schema_name
                FROM pg_catalog.pg_views v
                JOIN pg_catalog.pg_class c ON v.viewname = c.relname
                JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid AND n.nspname = v.schemaname
                WHERE v.schemaname = ?
                ORDER BY v.viewname
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String viewName = rs.getString("view_name");
                String viewDefinition = rs.getString("view_definition");
                String owner = rs.getString("owner");

                // Build CREATE VIEW statement
                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE VIEW ").append(schema.name()).append(".").append(viewName);
                ddl.append(" AS\n");
                ddl.append(viewDefinition);
                ddl.append(";");

                // Add owner information as comment
                if (owner != null && !owner.isEmpty()) {
                    ddl.append("\n\n-- Owner: ").append(owner);
                }

                return new DdlStatement(viewName, schema.name(), "views", ddl.toString());
            });
        } catch (SQLException e) {
            log.error("Failed to extract views from PostgreSQL schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract views: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractIndexes(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    c.relname as index_name,
                    t.relname as table_name,
                    a.attname as column_name,
                    i.indisunique as is_unique,
                    i.indisprimary as is_primary,
                    am.amname as index_type,
                    pg_get_indexdef(i.indexrelid) as index_definition
                FROM pg_catalog.pg_index i
                JOIN pg_catalog.pg_class c ON i.indexrelid = c.oid
                JOIN pg_catalog.pg_class t ON i.indrelid = t.oid
                JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid
                JOIN pg_catalog.pg_am am ON c.relam = am.oid
                JOIN pg_catalog.pg_attribute a ON i.indrelid = a.attrelid AND a.attnum = ANY(i.indkey)
                WHERE n.nspname = ?
                  AND t.relkind = 'r'  -- regular tables only
                  AND c.relkind = 'i'  -- indexes only
                ORDER BY t.relname, c.relname, array_position(i.indkey, a.attnum)
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
                String indexDefinition = null;

                while (rs.next()) {
                    String indexName = rs.getString("index_name");
                    String tableName = rs.getString("table_name");
                    String columnName = rs.getString("column_name");

                    if (currentIndex == null) {
                        currentIndex = indexName;
                        currentTable = tableName;
                        isUnique = rs.getBoolean("is_unique");
                        isPrimary = rs.getBoolean("is_primary");
                        indexType = rs.getString("index_type");
                        indexDefinition = rs.getString("index_definition");
                    } else if (!indexName.equals(currentIndex)) {
                        // Finished processing previous index
                        ddlStatements.add(createIndexDdl(schema.name(), currentTable, currentIndex,
                                columns, isUnique, isPrimary, indexType, indexDefinition));
                        columns = new ArrayList<>();
                        currentIndex = indexName;
                        currentTable = tableName;
                        isUnique = rs.getBoolean("is_unique");
                        isPrimary = rs.getBoolean("is_primary");
                        indexType = rs.getString("index_type");
                        indexDefinition = rs.getString("index_definition");
                    }

                    if (columnName != null) {
                        columns.add(columnName);
                    }
                }

                // Add the last index
                if (currentIndex != null) {
                    ddlStatements.add(createIndexDdl(schema.name(), currentTable, currentIndex,
                            columns, isUnique, isPrimary, indexType, indexDefinition));
                }

                log.debug("Extracted {} indexes from schema {}", ddlStatements.size(), schema.name());
                return ddlStatements;
            });
        } catch (SQLException e) {
            log.error("Failed to extract indexes from PostgreSQL schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract indexes: " + e.getMessage(), e);
        }
    }

    private DdlStatement createIndexDdl(String schemaName, String tableName, String indexName,
            List<String> columns, boolean isUnique, boolean isPrimary,
            String indexType, String indexDefinition) {
        // If we have the full index definition from pg_get_indexdef, use it
        if (indexDefinition != null && !indexDefinition.isEmpty()) {
            // Ensure schema is included in the definition
            if (!indexDefinition.contains(schemaName + ".")) {
                // Try to prepend schema if not present
                indexDefinition = indexDefinition.replace("ON " + tableName,
                        "ON " + schemaName + "." + tableName);
            }
            return new DdlStatement(indexName, schemaName, "indexes", indexDefinition + ";");
        }

        // Otherwise build index DDL manually
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

        // Add index type if specified and not default (btree)
        if (indexType != null && !indexType.equalsIgnoreCase("btree")) {
            ddl.append(" USING ").append(indexType.toUpperCase());
        }

        ddl.append(";");
        return new DdlStatement(indexName, schemaName, "indexes", ddl.toString());
    }

    @Override
    public List<DdlStatement> extractStoredProcedures(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    p.proname as procedure_name,
                    pg_get_functiondef(p.oid) as procedure_definition,
                    n.nspname as schema_name,
                    l.lanname as language,
                    p.provolatile as volatility
                FROM pg_catalog.pg_proc p
                JOIN pg_catalog.pg_namespace n ON p.pronamespace = n.oid
                JOIN pg_catalog.pg_language l ON p.prolang = l.oid
                WHERE n.nspname = ?
                  AND p.prokind = 'p'  -- procedures only
                  AND n.nspname NOT IN ('pg_catalog', 'information_schema')
                ORDER BY p.proname
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String procedureName = rs.getString("procedure_name");
                String procedureDefinition = rs.getString("procedure_definition");
                String language = rs.getString("language");
                String volatility = rs.getString("volatility");

                // Ensure the definition ends with semicolon
                if (procedureDefinition != null && !procedureDefinition.trim().endsWith(";")) {
                    procedureDefinition = procedureDefinition.trim() + ";";
                }

                // If we got the full definition, use it directly
                if (procedureDefinition != null && !procedureDefinition.isEmpty()) {
                    return new DdlStatement(procedureName, schema.name(), "procedures", procedureDefinition);
                } else {
                    // Fallback: build basic CREATE PROCEDURE statement
                    StringBuilder ddl = new StringBuilder();
                    ddl.append("CREATE OR REPLACE PROCEDURE ").append(schema.name()).append(".").append(procedureName)
                            .append("() LANGUAGE ").append(language);
                    if ("i".equals(volatility)) {
                        ddl.append(" IMMUTABLE");
                    } else if ("s".equals(volatility)) {
                        ddl.append(" STABLE");
                    } else if ("v".equals(volatility)) {
                        ddl.append(" VOLATILE");
                    }
                    ddl.append(";");
                    return new DdlStatement(procedureName, schema.name(), "procedures", ddl.toString());
                }
            });
        } catch (SQLException e) {
            log.error("Failed to extract stored procedures from PostgreSQL schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract stored procedures: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractFunctions(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    p.proname as function_name,
                    pg_get_functiondef(p.oid) as function_definition,
                    n.nspname as schema_name,
                    l.lanname as language,
                    p.provolatile as volatility,
                    pg_catalog.pg_get_function_result(p.oid) as return_type,
                    pg_catalog.pg_get_function_arguments(p.oid) as arguments
                FROM pg_catalog.pg_proc p
                JOIN pg_catalog.pg_namespace n ON p.pronamespace = n.oid
                JOIN pg_catalog.pg_language l ON p.prolang = l.oid
                WHERE n.nspname = ?
                  AND p.prokind = 'f'  -- functions only
                  AND n.nspname NOT IN ('pg_catalog', 'information_schema')
                ORDER BY p.proname
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String functionName = rs.getString("function_name");
                String functionDefinition = rs.getString("function_definition");
                String language = rs.getString("language");
                String volatility = rs.getString("volatility");
                String returnType = rs.getString("return_type");
                String arguments = rs.getString("arguments");

                // Ensure the definition ends with semicolon
                if (functionDefinition != null && !functionDefinition.trim().endsWith(";")) {
                    functionDefinition = functionDefinition.trim() + ";";
                }

                // If we got the full definition, use it directly
                if (functionDefinition != null && !functionDefinition.isEmpty()) {
                    return new DdlStatement(functionName, schema.name(), "functions", functionDefinition);
                } else {
                    // Fallback: build basic CREATE FUNCTION statement
                    StringBuilder ddl = new StringBuilder();
                    ddl.append("CREATE OR REPLACE FUNCTION ").append(schema.name()).append(".").append(functionName)
                            .append("(").append(arguments != null ? arguments : "").append(")")
                            .append(" RETURNS ").append(returnType != null ? returnType : "void")
                            .append(" LANGUAGE ").append(language);
                    if ("i".equals(volatility)) {
                        ddl.append(" IMMUTABLE");
                    } else if ("s".equals(volatility)) {
                        ddl.append(" STABLE");
                    } else if ("v".equals(volatility)) {
                        ddl.append(" VOLATILE");
                    }
                    ddl.append(";");
                    return new DdlStatement(functionName, schema.name(), "functions", ddl.toString());
                }
            });
        } catch (SQLException e) {
            log.error("Failed to extract functions from PostgreSQL schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract functions: " + e.getMessage(), e);
        }
    }

    @Override
    public List<DdlStatement> extractSequences(Schema schema, DatabaseConnection connection) {
        String query = """
                SELECT
                    c.relname as sequence_name,
                    s.seqstart as start_value,
                    s.seqincrement as increment_by,
                    s.seqmax as max_value,
                    s.seqmin as min_value,
                    s.seqcache as cache_size,
                    s.seqcycle as is_cycled,
                    n.nspname as schema_name
                FROM pg_catalog.pg_sequence s
                JOIN pg_catalog.pg_class c ON s.seqrelid = c.oid
                JOIN pg_catalog.pg_namespace n ON c.relnamespace = n.oid
                WHERE n.nspname = ?
                  AND n.nspname NOT IN ('pg_catalog', 'information_schema')
                ORDER BY c.relname
                """;
        try (Connection conn = connectionFactory.createConnection(connection)) {
            List<Object> params = List.of(schema.name());
            return jdbcTemplate.queryForList(conn, query, params, rs -> {
                String sequenceName = rs.getString("sequence_name");
                Long startValue = rs.getObject("start_value", Long.class);
                Long incrementBy = rs.getObject("increment_by", Long.class);
                Long maxValue = rs.getObject("max_value", Long.class);
                Long minValue = rs.getObject("min_value", Long.class);
                Long cacheSize = rs.getObject("cache_size", Long.class);
                Boolean isCycled = rs.getObject("is_cycled", Boolean.class);

                StringBuilder ddl = new StringBuilder();
                ddl.append("CREATE SEQUENCE ").append(schema.name()).append(".").append(sequenceName);
                if (startValue != null) {
                    ddl.append("\n    START WITH ").append(startValue);
                }
                if (incrementBy != null) {
                    ddl.append("\n    INCREMENT BY ").append(incrementBy);
                }
                if (minValue != null) {
                    ddl.append("\n    MINVALUE ").append(minValue);
                }
                if (maxValue != null) {
                    ddl.append("\n    MAXVALUE ").append(maxValue);
                }
                if (cacheSize != null) {
                    ddl.append("\n    CACHE ").append(cacheSize);
                }
                if (isCycled != null && isCycled) {
                    ddl.append("\n    CYCLE");
                } else {
                    ddl.append("\n    NO CYCLE");
                }
                ddl.append(";");
                return new DdlStatement(sequenceName, schema.name(), "sequences", ddl.toString());
            });
        } catch (SQLException e) {
            log.error("Failed to extract sequences from PostgreSQL schema {}", schema.name(), e);
            throw new ExtractionFailedException("Failed to extract sequences: " + e.getMessage(), e);
        }
    }
}