package pe.openstrategy.databaseproject.application.usecase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.openstrategy.databaseproject.application.exception.DatabaseConnectionException;
import pe.openstrategy.databaseproject.application.exception.ExtractionFailedException;
import pe.openstrategy.databaseproject.application.exception.FileWriteException;
import pe.openstrategy.databaseproject.application.exception.UnsupportedDatabaseTypeException;
import pe.openstrategy.databaseproject.domain.DatabaseType;
import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;
import pe.openstrategy.databaseproject.domain.valueobject.DdlStatement;
import pe.openstrategy.databaseproject.domain.valueobject.Schema;
import pe.openstrategy.databaseproject.port.out.DdlExtractor;
import pe.openstrategy.databaseproject.port.out.DatabaseConnectionValidator;
import pe.openstrategy.databaseproject.port.out.FileWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExtractDatabaseStructureUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ExtractDatabaseStructureUseCaseTest {

    @Mock
    private DdlExtractor extractor;

    @Mock
    private DatabaseConnectionValidator connectionValidator;

    @Mock
    private FileWriter fileWriter;

    private ExtractDatabaseStructureUseCase useCase;

    @BeforeEach
    void setUp() {
        // Create registry with a single extractor for POSTGRESQL
        Map<DatabaseType, DdlExtractor> registry = Map.of(DatabaseType.POSTGRESQL, extractor);
        useCase = new ExtractDatabaseStructureUseCase(registry, connectionValidator, fileWriter);
    }

    @Test
    @DisplayName("execute should succeed for valid request")
    void execute_shouldSucceedForValidRequest() {
        // Given
        DatabaseConnection connection = new DatabaseConnection(
                "jdbc:postgresql://localhost/test",
                "user",
                "pass",
                DatabaseType.POSTGRESQL,
                null
        );
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                "/output",
                null,
                null
        );

        Schema schema = new Schema("public");
        DdlStatement ddl = new DdlStatement("users", "public", "table", "CREATE TABLE public.users (id INT)");

        when(connectionValidator.isValid(connection)).thenReturn(true);
        when(extractor.extractSchemas(connection)).thenReturn(List.of(schema));
        when(extractor.extractTables(schema, connection)).thenReturn(List.of(ddl));
        when(extractor.extractViews(schema, connection)).thenReturn(List.of());
        when(extractor.extractIndexes(schema, connection)).thenReturn(List.of());
        when(extractor.extractStoredProcedures(schema, connection)).thenReturn(List.of());
        when(extractor.extractFunctions(schema, connection)).thenReturn(List.of());
        when(extractor.extractSequences(schema, connection)).thenReturn(List.of());

        // When
        ExtractionResult result = useCase.execute(request);

        // Then
        assertTrue(result.success());
        assertEquals("Extraction completed successfully", result.message());
        assertEquals("/output", result.outputPath());
        assertEquals(1, result.objectCounts().size());
        assertEquals(1, result.objectCounts().get("table"));
        verify(fileWriter).writeDdl("/output", List.of(ddl));
    }

    @Test
    @DisplayName("execute should throw UnsupportedDatabaseTypeException for unknown database type")
    void execute_whenDatabaseTypeUnsupported_shouldThrowUnsupportedDatabaseTypeException() {
        DatabaseConnection connection = new DatabaseConnection(
                "jdbc:unknown://localhost/test",
                "user",
                "pass",
                DatabaseType.POSTGRESQL,
                null
        );
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.ORACLE, // Not in registry
                connection,
                "/output",
                null,
                null
        );

        when(connectionValidator.isValid(connection)).thenReturn(true);

        UnsupportedDatabaseTypeException ex = assertThrows(UnsupportedDatabaseTypeException.class,
                () -> useCase.execute(request));
        assertTrue(ex.getMessage().contains("Unsupported database type"));
    }

    @Test
    @DisplayName("execute should throw DatabaseConnectionException when validation fails")
    void execute_whenConnectionValidationFails_shouldThrowDatabaseConnectionException() {
        DatabaseConnection connection = new DatabaseConnection(
                "jdbc:postgresql://localhost/test",
                "user",
                "pass",
                DatabaseType.POSTGRESQL,
                null
        );
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                "/output",
                null,
                null
        );

        when(connectionValidator.isValid(connection)).thenReturn(false);

        DatabaseConnectionException ex = assertThrows(DatabaseConnectionException.class,
                () -> useCase.execute(request));
        assertEquals("Database connection validation failed", ex.getMessage());
    }

    @Test
    @DisplayName("execute should filter schemas when schema filter provided")
    void execute_shouldFilterSchemasWhenSchemaFilterProvided() {
        DatabaseConnection connection = new DatabaseConnection(
                "jdbc:postgresql://localhost/test",
                "user",
                "pass",
                DatabaseType.POSTGRESQL,
                null
        );
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                "/output",
                List.of("public", "sales"),
                null
        );

        Schema publicSchema = new Schema("public");
        Schema salesSchema = new Schema("sales");
        Schema otherSchema = new Schema("other");
        DdlStatement ddl = new DdlStatement("users", "public", "table", "CREATE TABLE public.users (id INT)");

        when(connectionValidator.isValid(connection)).thenReturn(true);
        when(extractor.extractSchemas(connection)).thenReturn(List.of(publicSchema, salesSchema, otherSchema));
        when(extractor.extractTables(publicSchema, connection)).thenReturn(List.of(ddl));
        when(extractor.extractTables(salesSchema, connection)).thenReturn(List.of());

        // other object types return empty
        when(extractor.extractViews(any(), eq(connection))).thenReturn(List.of());
        when(extractor.extractIndexes(any(), eq(connection))).thenReturn(List.of());
        when(extractor.extractStoredProcedures(any(), eq(connection))).thenReturn(List.of());
        when(extractor.extractFunctions(any(), eq(connection))).thenReturn(List.of());
        when(extractor.extractSequences(any(), eq(connection))).thenReturn(List.of());

        ExtractionResult result = useCase.execute(request);

        assertTrue(result.success());
        // Only tables from public schema counted
        assertEquals(1, result.objectCounts().get("table"));
        verify(extractor, never()).extractTables(otherSchema, connection);
    }

    @Test
    @DisplayName("execute should filter object types when object type filter provided")
    void execute_shouldFilterObjectTypesWhenObjectTypeFilterProvided() {
        DatabaseConnection connection = new DatabaseConnection(
                "jdbc:postgresql://localhost/test",
                "user",
                "pass",
                DatabaseType.POSTGRESQL,
                null
        );
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                "/output",
                null,
                List.of("table", "view")
        );

        Schema schema = new Schema("public");
        DdlStatement tableDdl = new DdlStatement("users", "public", "table", "CREATE TABLE public.users (id INT)");
        DdlStatement viewDdl = new DdlStatement("v", "public", "view", "CREATE VIEW public.v AS SELECT 1");
        DdlStatement indexDdl = new DdlStatement("idx", "public", "index", "CREATE INDEX idx ON public.users(id)");

        when(connectionValidator.isValid(connection)).thenReturn(true);
        when(extractor.extractSchemas(connection)).thenReturn(List.of(schema));
        when(extractor.extractTables(schema, connection)).thenReturn(List.of(tableDdl));
        when(extractor.extractViews(schema, connection)).thenReturn(List.of(viewDdl));
        // other object types empty

        ExtractionResult result = useCase.execute(request);

        assertTrue(result.success());
        Map<String, Integer> counts = result.objectCounts();
        assertEquals(1, counts.get("table"));
        assertEquals(1, counts.get("view"));
        assertNull(counts.get("index")); // index not extracted
    }

    @Test
    @DisplayName("execute should throw ExtractionFailedException when no schemas match filter")
    void execute_whenNoSchemasMatchFilter_shouldThrowExtractionFailedException() {
        DatabaseConnection connection = new DatabaseConnection(
                "jdbc:postgresql://localhost/test",
                "user",
                "pass",
                DatabaseType.POSTGRESQL,
                null
        );
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                "/output",
                List.of("nonexistent"),
                null
        );

        Schema schema = new Schema("public");

        when(connectionValidator.isValid(connection)).thenReturn(true);
        when(extractor.extractSchemas(connection)).thenReturn(List.of(schema));

        ExtractionFailedException ex = assertThrows(ExtractionFailedException.class,
                () -> useCase.execute(request));
        assertEquals("No schemas found matching criteria", ex.getMessage());
    }

    @Test
    @DisplayName("execute should throw ExtractionFailedException when extractor throws")
    void execute_whenExtractorThrows_shouldThrowExtractionFailedException() {
        DatabaseConnection connection = new DatabaseConnection(
                "jdbc:postgresql://localhost/test",
                "user",
                "pass",
                DatabaseType.POSTGRESQL,
                null
        );
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                "/output",
                null,
                null
        );

        Schema schema = new Schema("public");

        when(connectionValidator.isValid(connection)).thenReturn(true);
        when(extractor.extractSchemas(connection)).thenReturn(List.of(schema));
        when(extractor.extractTables(schema, connection)).thenThrow(new RuntimeException("DB error"));

        ExtractionFailedException ex = assertThrows(ExtractionFailedException.class,
                () -> useCase.execute(request));
        assertTrue(ex.getMessage().contains("Unexpected error during extraction"));
    }

    @Test
    @DisplayName("execute should throw FileWriteException when file writer fails")
    void execute_whenFileWriterFails_shouldThrowFileWriteException() {
        DatabaseConnection connection = new DatabaseConnection(
                "jdbc:postgresql://localhost/test",
                "user",
                "pass",
                DatabaseType.POSTGRESQL,
                null
        );
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                "/output",
                null,
                null
        );

        Schema schema = new Schema("public");
        DdlStatement ddl = new DdlStatement("users", "public", "table", "CREATE TABLE public.users (id INT)");

        when(connectionValidator.isValid(connection)).thenReturn(true);
        when(extractor.extractSchemas(connection)).thenReturn(List.of(schema));
        when(extractor.extractTables(schema, connection)).thenReturn(List.of(ddl));
        // other object types empty
        doThrow(new FileWriteException("Disk full")).when(fileWriter).writeDdl(anyString(), anyList());

        FileWriteException ex = assertThrows(FileWriteException.class,
                () -> useCase.execute(request));
        assertEquals("Disk full", ex.getMessage());
    }

    @Test
    @DisplayName("execute should handle object type filter case-insensitively")
    void execute_shouldHandleObjectTypeFilterCaseInsensitively() {
        DatabaseConnection connection = new DatabaseConnection(
                "jdbc:postgresql://localhost/test",
                "user",
                "pass",
                DatabaseType.POSTGRESQL,
                null
        );
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                "/output",
                null,
                List.of("TABLE", "View") // mixed case
        );

        Schema schema = new Schema("public");
        DdlStatement tableDdl = new DdlStatement("users", "public", "table", "CREATE TABLE public.users (id INT)");
        DdlStatement viewDdl = new DdlStatement("v", "public", "view", "CREATE VIEW public.v AS SELECT 1");

        when(connectionValidator.isValid(connection)).thenReturn(true);
        when(extractor.extractSchemas(connection)).thenReturn(List.of(schema));
        when(extractor.extractTables(schema, connection)).thenReturn(List.of(tableDdl));
        when(extractor.extractViews(schema, connection)).thenReturn(List.of(viewDdl));
        // other object types empty

        ExtractionResult result = useCase.execute(request);

        assertTrue(result.success());
        Map<String, Integer> counts = result.objectCounts();
        assertEquals(1, counts.get("table"));
        assertEquals(1, counts.get("view"));
    }

    @Test
    @DisplayName("execute should ignore unknown object type strings in filter")
    void execute_shouldIgnoreUnknownObjectTypeStringsInFilter() {
        DatabaseConnection connection = new DatabaseConnection(
                "jdbc:postgresql://localhost/test",
                "user",
                "pass",
                DatabaseType.POSTGRESQL,
                null
        );
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                "/output",
                null,
                List.of("table", "unknown", "view")
        );

        Schema schema = new Schema("public");
        DdlStatement tableDdl = new DdlStatement("users", "public", "table", "CREATE TABLE public.users (id INT)");
        DdlStatement viewDdl = new DdlStatement("v", "public", "view", "CREATE VIEW public.v AS SELECT 1");

        when(connectionValidator.isValid(connection)).thenReturn(true);
        when(extractor.extractSchemas(connection)).thenReturn(List.of(schema));
        when(extractor.extractTables(schema, connection)).thenReturn(List.of(tableDdl));
        when(extractor.extractViews(schema, connection)).thenReturn(List.of(viewDdl));
        // other object types empty

        ExtractionResult result = useCase.execute(request);

        assertTrue(result.success());
        Map<String, Integer> counts = result.objectCounts();
        assertEquals(1, counts.get("table"));
        assertEquals(1, counts.get("view"));
        assertNull(counts.get("unknown"));
    }
}