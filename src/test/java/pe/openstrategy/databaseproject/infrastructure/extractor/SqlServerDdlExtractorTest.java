package pe.openstrategy.databaseproject.infrastructure.extractor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.openstrategy.databaseproject.application.exception.ExtractionFailedException;
import pe.openstrategy.databaseproject.domain.DatabaseType;
import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;
import pe.openstrategy.databaseproject.domain.valueobject.DdlStatement;
import pe.openstrategy.databaseproject.domain.valueobject.Schema;
import pe.openstrategy.databaseproject.infrastructure.jdbc.JdbcConnectionFactory;
import pe.openstrategy.databaseproject.infrastructure.jdbc.JdbcTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SqlServerDdlExtractor}.
 */
@ExtendWith(MockitoExtension.class)
class SqlServerDdlExtractorTest {

    @Mock
    private JdbcConnectionFactory connectionFactory;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Connection connection;

    private SqlServerDdlExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new SqlServerDdlExtractor(connectionFactory, jdbcTemplate);
    }

    @Test
    @DisplayName("extractSchemas should return filtered schemas")
    void extractSchemas_shouldReturnFilteredSchemas() throws SQLException {
        // Given
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:sqlserver://localhost:1433;databaseName=test",
                "user",
                "pass",
                DatabaseType.SQL_SERVER,
                null
        );
        Schema schema1 = new Schema("Sales");
        Schema schema2 = new Schema("HR");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), any()))
                .thenReturn(List.of(schema1, schema2));

        // When
        List<Schema> schemas = extractor.extractSchemas(dbConn);

        // Then
        assertEquals(2, schemas.size());
        assertTrue(schemas.stream().anyMatch(s -> s.name().equals("Sales")));
        assertTrue(schemas.stream().anyMatch(s -> s.name().equals("HR")));
        verify(connection).close();
    }

    @Test
    @DisplayName("extractSchemas should throw ExtractionFailedException on SQL error")
    void extractSchemas_whenSqlError_shouldThrowExtractionFailedException() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:sqlserver://localhost:1433;databaseName=test",
                "user",
                "pass",
                DatabaseType.SQL_SERVER,
                null
        );

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), any()))
                .thenThrow(new SQLException("Connection failed"));

        ExtractionFailedException ex = assertThrows(ExtractionFailedException.class,
                () -> extractor.extractSchemas(dbConn));
        assertTrue(ex.getMessage().contains("Failed to extract schemas"));
        verify(connection).close();
    }

    @Test
    @DisplayName("extractTables should return DDL statements")
    void extractTables_shouldReturnDdlStatements() throws SQLException {
        // Given
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:sqlserver://localhost:1433;databaseName=test",
                "user",
                "pass",
                DatabaseType.SQL_SERVER,
                null
        );
        Schema schema = new Schema("Sales");
        DdlStatement expectedDdl = new DdlStatement("Orders", "Sales", "tables",
                "CREATE TABLE Sales.Orders (\n    OrderID INT NOT NULL,\n    CustomerName NVARCHAR(100)\n);");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.query(eq(connection), anyString(), eq(List.of("Sales")), any()))
                .thenReturn(List.of(expectedDdl));

        // When
        List<DdlStatement> tables = extractor.extractTables(schema, dbConn);

        // Then
        assertEquals(1, tables.size());
        assertEquals(expectedDdl, tables.get(0));
        verify(connection).close();
    }

    @Test
    @DisplayName("extractTables should handle empty result set")
    void extractTables_whenNoTables_shouldReturnEmptyList() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:sqlserver://localhost:1433;databaseName=test",
                "user",
                "pass",
                DatabaseType.SQL_SERVER,
                null
        );
        Schema schema = new Schema("Sales");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.query(eq(connection), anyString(), eq(List.of("Sales")), any()))
                .thenReturn(List.of());

        List<DdlStatement> tables = extractor.extractTables(schema, dbConn);

        assertTrue(tables.isEmpty());
        verify(connection).close();
    }

    @Test
    @DisplayName("extractViews should return DDL statements")
    void extractViews_shouldReturnDdlStatements() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:sqlserver://localhost:1433;databaseName=test",
                "user",
                "pass",
                DatabaseType.SQL_SERVER,
                null
        );
        Schema schema = new Schema("Sales");
        DdlStatement expectedDdl = new DdlStatement("ActiveOrders", "Sales", "views",
                "CREATE VIEW Sales.ActiveOrders AS\nSELECT * FROM Orders WHERE Status = 'Active';");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("Sales")), any()))
                .thenReturn(List.of(expectedDdl));

        List<DdlStatement> views = extractor.extractViews(schema, dbConn);

        assertEquals(1, views.size());
        assertEquals(expectedDdl, views.get(0));
        verify(connection).close();
    }

    @Test
    @DisplayName("extractIndexes should return DDL statements")
    void extractIndexes_shouldReturnDdlStatements() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:sqlserver://localhost:1433;databaseName=test",
                "user",
                "pass",
                DatabaseType.SQL_SERVER,
                null
        );
        Schema schema = new Schema("Sales");
        DdlStatement expectedDdl = new DdlStatement("IX_Orders_Customer", "Sales", "indexes",
                "CREATE NONCLUSTERED INDEX IX_Orders_Customer ON Sales.Orders (CustomerID);");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.query(eq(connection), anyString(), eq(List.of("Sales")), any()))
                .thenReturn(List.of(expectedDdl));

        List<DdlStatement> indexes = extractor.extractIndexes(schema, dbConn);

        assertEquals(1, indexes.size());
        assertEquals(expectedDdl, indexes.get(0));
        verify(connection).close();
    }

    @Test
    @DisplayName("extractStoredProcedures should return DDL statements")
    void extractStoredProcedures_shouldReturnDdlStatements() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:sqlserver://localhost:1433;databaseName=test",
                "user",
                "pass",
                DatabaseType.SQL_SERVER,
                null
        );
        Schema schema = new Schema("Sales");
        DdlStatement expectedDdl = new DdlStatement("sp_ProcessOrder", "Sales", "procedures",
                "CREATE PROCEDURE Sales.sp_ProcessOrder AS\nBEGIN\n    -- Procedure definition\nEND;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("Sales")), any()))
                .thenReturn(List.of(expectedDdl));

        List<DdlStatement> procedures = extractor.extractStoredProcedures(schema, dbConn);

        assertEquals(1, procedures.size());
        assertEquals(expectedDdl, procedures.get(0));
        verify(connection).close();
    }

    @Test
    @DisplayName("extractFunctions should return DDL statements")
    void extractFunctions_shouldReturnDdlStatements() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:sqlserver://localhost:1433;databaseName=test",
                "user",
                "pass",
                DatabaseType.SQL_SERVER,
                null
        );
        Schema schema = new Schema("Sales");
        DdlStatement expectedDdl = new DdlStatement("fn_CalculateTotal", "Sales", "functions",
                "CREATE FUNCTION Sales.fn_CalculateTotal(@OrderID INT) RETURNS DECIMAL AS\nBEGIN\n    RETURN 0.0;\nEND;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("Sales")), any()))
                .thenReturn(List.of(expectedDdl));

        List<DdlStatement> functions = extractor.extractFunctions(schema, dbConn);

        assertEquals(1, functions.size());
        assertEquals(expectedDdl, functions.get(0));
        verify(connection).close();
    }

    @Test
    @DisplayName("extractSequences should return DDL statements")
    void extractSequences_shouldReturnDdlStatements() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:sqlserver://localhost:1433;databaseName=test",
                "user",
                "pass",
                DatabaseType.SQL_SERVER,
                null
        );
        Schema schema = new Schema("Sales");
        DdlStatement expectedDdl = new DdlStatement("Seq_OrderID", "Sales", "sequences",
                "CREATE SEQUENCE Sales.Seq_OrderID\n    START WITH 1\n    INCREMENT BY 1\n    NO CACHE\n    NO CYCLE;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("Sales")), any()))
                .thenReturn(List.of(expectedDdl));

        List<DdlStatement> sequences = extractor.extractSequences(schema, dbConn);

        assertEquals(1, sequences.size());
        assertEquals(expectedDdl, sequences.get(0));
        verify(connection).close();
    }

    @Test
    @DisplayName("All extraction methods should propagate SQL exceptions")
    void extractMethods_whenSqlError_shouldThrowExtractionFailedException() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:sqlserver://localhost:1433;databaseName=test",
                "user",
                "pass",
                DatabaseType.SQL_SERVER,
                null
        );
        Schema schema = new Schema("Sales");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        lenient().when(jdbcTemplate.query(any(Connection.class), anyString(), any(List.class), any()))
                .thenThrow(new SQLException("Database error"));
        lenient().when(jdbcTemplate.queryForList(any(Connection.class), anyString(), any()))
                .thenThrow(new SQLException("Database error"));
        lenient().when(jdbcTemplate.queryForList(any(Connection.class), anyString(), any(List.class), any()))
                .thenThrow(new SQLException("Database error"));

        assertThrows(ExtractionFailedException.class, () -> extractor.extractSchemas(dbConn));
        assertThrows(ExtractionFailedException.class, () -> extractor.extractTables(schema, dbConn));
        assertThrows(ExtractionFailedException.class, () -> extractor.extractViews(schema, dbConn));
        assertThrows(ExtractionFailedException.class, () -> extractor.extractIndexes(schema, dbConn));
        assertThrows(ExtractionFailedException.class, () -> extractor.extractStoredProcedures(schema, dbConn));
        assertThrows(ExtractionFailedException.class, () -> extractor.extractFunctions(schema, dbConn));
        assertThrows(ExtractionFailedException.class, () -> extractor.extractSequences(schema, dbConn));
    }
}