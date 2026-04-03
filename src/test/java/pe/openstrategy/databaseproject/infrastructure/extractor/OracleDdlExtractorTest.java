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
 * Unit tests for {@link OracleDdlExtractor}.
 */
@ExtendWith(MockitoExtension.class)
class OracleDdlExtractorTest {

    @Mock
    private JdbcConnectionFactory connectionFactory;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Connection connection;

    private OracleDdlExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new OracleDdlExtractor(connectionFactory, jdbcTemplate);
    }

    @Test
    @DisplayName("extractSchemas should return filtered schemas excluding system schemas")
    void extractSchemas_shouldReturnFilteredSchemas() throws SQLException {
        // Given
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
                null
        );
        Schema schema1 = new Schema("APPUSER");
        Schema schema2 = new Schema("MYSCHEMA");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), any()))
                .thenReturn(List.of(schema1, schema2));

        // When
        List<Schema> schemas = extractor.extractSchemas(dbConn);

        // Then
        assertEquals(2, schemas.size());
        assertTrue(schemas.stream().anyMatch(s -> s.name().equals("APPUSER")));
        assertTrue(schemas.stream().anyMatch(s -> s.name().equals("MYSCHEMA")));
        verify(connection).close();
    }

    @Test
    @DisplayName("extractSchemas should filter out system schemas")
    void extractSchemas_shouldFilterSystemSchemas() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
                null
        );
        Schema systemSchema = new Schema("SYS");
        Schema userSchema = new Schema("MYAPP");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), any()))
                .thenReturn(List.of(systemSchema, userSchema));

        List<Schema> schemas = extractor.extractSchemas(dbConn);

        assertEquals(1, schemas.size());
        assertEquals("MYAPP", schemas.get(0).name());
        verify(connection).close();
    }

    @Test
    @DisplayName("extractSchemas should throw ExtractionFailedException on SQL error")
    void extractSchemas_whenSqlError_shouldThrowExtractionFailedException() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
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
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
                null
        );
        Schema schema = new Schema("MYSCHEMA");
        DdlStatement expectedDdl = new DdlStatement("EMPLOYEES", "MYSCHEMA", "tables",
                "CREATE TABLE MYSCHEMA.EMPLOYEES (\n    EMPLOYEE_ID NUMBER(10) NOT NULL,\n    NAME VARCHAR2(100)\n);");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.query(eq(connection), anyString(), eq(List.of("MYSCHEMA")), any()))
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
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
                null
        );
        Schema schema = new Schema("MYSCHEMA");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.query(eq(connection), anyString(), eq(List.of("MYSCHEMA")), any()))
                .thenReturn(List.of());

        List<DdlStatement> tables = extractor.extractTables(schema, dbConn);

        assertTrue(tables.isEmpty());
        verify(connection).close();
    }

    @Test
    @DisplayName("extractViews should return DDL statements")
    void extractViews_shouldReturnDdlStatements() throws SQLException {
        DatabaseConnection dbConn = new DatabaseConnection(
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
                null
        );
        Schema schema = new Schema("MYSCHEMA");
        DdlStatement expectedDdl = new DdlStatement("ACTIVE_EMPLOYEES", "MYSCHEMA", "views",
                "CREATE VIEW MYSCHEMA.ACTIVE_EMPLOYEES AS SELECT * FROM EMPLOYEES WHERE ACTIVE = 'Y';");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("MYSCHEMA")), any()))
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
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
                null
        );
        Schema schema = new Schema("MYSCHEMA");
        DdlStatement expectedDdl = new DdlStatement("IDX_EMP_EMAIL", "MYSCHEMA", "indexes",
                "CREATE UNIQUE INDEX IDX_EMP_EMAIL ON MYSCHEMA.EMPLOYEES (EMAIL);");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.query(eq(connection), anyString(), eq(List.of("MYSCHEMA")), any()))
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
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
                null
        );
        Schema schema = new Schema("MYSCHEMA");
        DdlStatement expectedDdl = new DdlStatement("UPDATE_SALARY", "MYSCHEMA", "procedures",
                "CREATE OR REPLACE PROCEDURE UPDATE_SALARY IS BEGIN NULL; END;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("MYSCHEMA")), any()))
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
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
                null
        );
        Schema schema = new Schema("MYSCHEMA");
        DdlStatement expectedDdl = new DdlStatement("CALCULATE_BONUS", "MYSCHEMA", "functions",
                "CREATE OR REPLACE FUNCTION CALCULATE_BONUS RETURN NUMBER IS BEGIN RETURN 0; END;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("MYSCHEMA")), any()))
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
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
                null
        );
        Schema schema = new Schema("MYSCHEMA");
        DdlStatement expectedDdl = new DdlStatement("EMPLOYEE_ID_SEQ", "MYSCHEMA", "sequences",
                "CREATE SEQUENCE EMPLOYEE_ID_SEQ\n    MINVALUE 1\n    MAXVALUE 999999\n    INCREMENT BY 1\n    NOCYCLE;");

        when(connectionFactory.createConnection(dbConn)).thenReturn(connection);
        when(jdbcTemplate.queryForList(eq(connection), anyString(), eq(List.of("MYSCHEMA")), any()))
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
                "jdbc:oracle:thin:@localhost:1521/test",
                "user",
                "pass",
                DatabaseType.ORACLE,
                null
        );
        Schema schema = new Schema("MYSCHEMA");

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
