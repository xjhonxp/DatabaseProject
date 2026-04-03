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
 * Unit tests for {@link MariaDbDdlExtractor}.
 */
@ExtendWith(MockitoExtension.class)
class MariaDbDdlExtractorTest {

    @Mock
    private JdbcConnectionFactory connectionFactory;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Connection connection;

    private MariaDbDdlExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new MariaDbDdlExtractor(connectionFactory, jdbcTemplate);
    }

    @Test
    @DisplayName("extractSchemas should return filtered schemas")
    void extractSchemas_shouldReturnFilteredSchemas() throws SQLException {
        // Given
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:mariadb://localhost:3306/test",
                "user",
                "pass",
                DatabaseType.MARIADB,
                null
        );
        Schema schema1 = new Schema("appdb");
        Schema schema2 = new Schema("testdb");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), any()))
                .thenReturn(List.of(schema1, schema2));

        // When
        List<Schema> schemas = extractor.extractSchemas(dbConn);

        // Then
        assertEquals(2, schemas.size());
        assertTrue(schemas.stream().anyMatch(s -> s.name().equals("appdb")));
        assertTrue(schemas.stream().anyMatch(s -> s.name().equals("testdb")));
        verify(connection).close();
    }

    @Test
    @DisplayName("extractSchemas should throw ExtractionFailedException on SQL error")
    void extractSchemas_whenSqlError_shouldThrowExtractionFailedException() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:mariadb://localhost:3306/test",
                "user",
                "pass",
                DatabaseType.MARIADB,
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
                "jdbc:mariadb://localhost:3306/test",
                "user",
                "pass",
                DatabaseType.MARIADB,
                null
        );
        Schema schema = new Schema("appdb");
        DdlStatement expectedDdl = new DdlStatement("users", "appdb", "tables",
                "CREATE TABLE appdb.users (\n    id INT NOT NULL,\n    name VARCHAR(100)\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.query(eq(connection), anyString(), eq(List.of("appdb")), any()))
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
                "jdbc:mariadb://localhost:3306/test",
                "user",
                "pass",
                DatabaseType.MARIADB,
                null
        );
        Schema schema = new Schema("appdb");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.query(eq(connection), anyString(), eq(List.of("appdb")), any()))
                .thenReturn(List.of());

        List<DdlStatement> tables = extractor.extractTables(schema, dbConn);

        assertTrue(tables.isEmpty());
        verify(connection).close();
    }

    @Test
    @DisplayName("extractViews should return DDL statements")
    void extractViews_shouldReturnDdlStatements() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:mariadb://localhost:3306/test",
                "user",
                "pass",
                DatabaseType.MARIADB,
                null
        );
        Schema schema = new Schema("appdb");
        DdlStatement expectedDdl = new DdlStatement("active_users", "appdb", "views",
                "CREATE VIEW appdb.active_users AS\nSELECT * FROM users WHERE active = 1;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("appdb")), any()))
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
                "jdbc:mariadb://localhost:3306/test",
                "user",
                "pass",
                DatabaseType.MARIADB,
                null
        );
        Schema schema = new Schema("appdb");
        DdlStatement expectedDdl = new DdlStatement("idx_users_email", "appdb", "indexes",
                "CREATE UNIQUE INDEX idx_users_email ON appdb.users (email);");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.query(eq(connection), anyString(), eq(List.of("appdb")), any()))
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
                "jdbc:mariadb://localhost:3306/test",
                "user",
                "pass",
                DatabaseType.MARIADB,
                null
        );
        Schema schema = new Schema("appdb");
        DdlStatement expectedDdl = new DdlStatement("update_user", "appdb", "procedures",
                "CREATE PROCEDURE appdb.update_user()\nBEGIN\n    -- Procedure definition\nEND;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("appdb")), any()))
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
                "jdbc:mariadb://localhost:3306/test",
                "user",
                "pass",
                DatabaseType.MARIADB,
                null
        );
        Schema schema = new Schema("appdb");
        DdlStatement expectedDdl = new DdlStatement("calculate_total", "appdb", "functions",
                "CREATE FUNCTION appdb.calculate_total() RETURNS DECIMAL(10,2)\nBEGIN\n    RETURN 0.0;\nEND;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("appdb")), any()))
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
                "jdbc:mariadb://localhost:3306/test",
                "user",
                "pass",
                DatabaseType.MARIADB,
                null
        );
        Schema schema = new Schema("appdb");
        DdlStatement expectedDdl = new DdlStatement("user_id_seq", "appdb", "sequences",
                "CREATE SEQUENCE appdb.user_id_seq\n    START WITH 1\n    INCREMENT BY 1\n    NO CACHE\n    NOCYCLE;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("appdb")), any()))
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
                "jdbc:mariadb://localhost:3306/test",
                "user",
                "pass",
                DatabaseType.MARIADB,
                null
        );
        Schema schema = new Schema("appdb");

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