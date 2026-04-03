package pe.openstrategy.databaseproject.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.openstrategy.databaseproject.application.usecase.ExtractionRequest;
import pe.openstrategy.databaseproject.application.usecase.ExtractionResult;
import pe.openstrategy.databaseproject.application.usecase.ExtractDatabaseStructureUseCase;
import pe.openstrategy.databaseproject.domain.DatabaseType;
import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for PostgreSQL extraction using Testcontainers.
 */
@SpringBootTest
@Testcontainers
@DisplayName("PostgreSQL Integration Test")
@Disabled("Requires Docker environment")
class PostgreSQLIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withExposedPorts(5432);

    @Autowired
    private ExtractDatabaseStructureUseCase extractDatabaseStructureUseCase;

    private static Path tempOutputDir;

    @BeforeAll
    static void beforeAll() throws Exception {
        // Create temporary output directory
        tempOutputDir = Files.createTempDirectory("database-extraction-test-");
        
        // Initialize database with test schema
        String jdbcUrl = postgres.getJdbcUrl();
        String username = postgres.getUsername();
        String password = postgres.getPassword();
        
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement()) {
            
            // Read and execute initialization script
            String initScript = new String(Files.readAllBytes(
                Paths.get("src/test/resources/init-test-db.sql")
            ));
            stmt.execute(initScript);
        }
    }

    @AfterAll
    static void afterAll() throws IOException {
        // Clean up temporary directory
        if (tempOutputDir != null && Files.exists(tempOutputDir)) {
            Files.walk(tempOutputDir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    @DisplayName("should extract PostgreSQL database metadata and generate DDL files")
    void shouldExtractPostgreSQLDatabaseMetadata() {
        // Given
        DatabaseConnection connection = new DatabaseConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                DatabaseType.POSTGRESQL,
                null
        );
        
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                tempOutputDir.toString(),
                List.of("test_schema"),
                List.of("table", "view", "index", "sequence", "function", "procedure")
        );

        // When
        ExtractionResult result = extractDatabaseStructureUseCase.execute(request);

        // Then
        assertTrue(result.success());
        assertNotNull(result.outputPath());
        assertTrue(Files.exists(Paths.get(result.outputPath())));

        // Verify directory structure
        Path schemaDir = tempOutputDir.resolve("test_schema");
        assertTrue(Files.exists(schemaDir));
        
        // Check that object type directories exist
        assertTrue(Files.exists(schemaDir.resolve("tables")));
        assertTrue(Files.exists(schemaDir.resolve("views")));
        assertTrue(Files.exists(schemaDir.resolve("indexes")));
        assertTrue(Files.exists(schemaDir.resolve("sequences")));
        assertTrue(Files.exists(schemaDir.resolve("functions")));
        assertTrue(Files.exists(schemaDir.resolve("procedures")));

        // Verify some files were created
        try {
            long tableFiles = Files.list(schemaDir.resolve("tables")).count();
            assertTrue(tableFiles >= 2, "Expected at least 2 table files");
            
            long viewFiles = Files.list(schemaDir.resolve("views")).count();
            assertTrue(viewFiles >= 1, "Expected at least 1 view file");
            
            long indexFiles = Files.list(schemaDir.resolve("indexes")).count();
            assertTrue(indexFiles >= 2, "Expected at least 2 index files");
            
            long sequenceFiles = Files.list(schemaDir.resolve("sequences")).count();
            assertTrue(sequenceFiles >= 1, "Expected at least 1 sequence file");
            
            long functionFiles = Files.list(schemaDir.resolve("functions")).count();
            assertTrue(functionFiles >= 1, "Expected at least 1 function file");
            
            long procedureFiles = Files.list(schemaDir.resolve("procedures")).count();
            assertTrue(procedureFiles >= 1, "Expected at least 1 procedure file");
            
        } catch (IOException e) {
            fail("Failed to list files: " + e.getMessage());
        }

        // Verify DDL content for a specific table
        Path usersTableFile = schemaDir.resolve("tables").resolve("users.sql");
        assertTrue(Files.exists(usersTableFile));
        
        try {
            String ddlContent = Files.readString(usersTableFile);
            assertTrue(ddlContent.contains("CREATE TABLE test_schema.users"));
            assertTrue(ddlContent.contains("id SERIAL PRIMARY KEY"));
            assertTrue(ddlContent.contains("username VARCHAR(50) NOT NULL"));
            assertTrue(ddlContent.contains("email VARCHAR(100) NOT NULL"));
        } catch (IOException e) {
            fail("Failed to read DDL file: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("should filter by object types")
    void shouldFilterByObjectTypes() {
        // Given: only extract tables
        DatabaseConnection connection = new DatabaseConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                DatabaseType.POSTGRESQL,
                null
        );
        
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                tempOutputDir.toString(),
                List.of("test_schema"),
                List.of("table")
        );

        // When
        ExtractionResult result = extractDatabaseStructureUseCase.execute(request);

        // Then
        assertTrue(result.success());
        
        Path schemaDir = tempOutputDir.resolve("test_schema");
        assertTrue(Files.exists(schemaDir.resolve("tables")));
        assertFalse(Files.exists(schemaDir.resolve("views"))); // Should not exist
        assertFalse(Files.exists(schemaDir.resolve("indexes")));
    }

    @Test
    @DisplayName("should filter by schema")
    void shouldFilterBySchema() {
        // Given: create another schema and extract only test_schema
        DatabaseConnection connection = new DatabaseConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword(),
                DatabaseType.POSTGRESQL,
                null
        );
        
        ExtractionRequest request = new ExtractionRequest(
                DatabaseType.POSTGRESQL,
                connection,
                tempOutputDir.toString(),
                List.of("test_schema"), // only this schema
                List.of("table")
        );

        // When
        ExtractionResult result = extractDatabaseStructureUseCase.execute(request);

        // Then
        assertTrue(result.success());
        
        // Should not create directory for other schemas (like public)
        Path publicDir = tempOutputDir.resolve("public");
        assertFalse(Files.exists(publicDir), "Should not create directory for filtered-out schema");
        
        Path testSchemaDir = tempOutputDir.resolve("test_schema");
        assertTrue(Files.exists(testSchemaDir), "Should create directory for included schema");
    }
}