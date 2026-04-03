package pe.openstrategy.databaseproject.infrastructure.filesystem;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.openstrategy.databaseproject.domain.valueobject.DdlStatement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LocalFileWriter}.
 */
class LocalFileWriterTest {

    @TempDir
    Path tempDir;

    private final LocalFileWriter fileWriter = new LocalFileWriter();

    @Test
    @DisplayName("writeDdl should create directory structure and files")
    void writeDdl_shouldCreateDirectoryStructureAndFiles() throws IOException {
        // Given
        String basePath = tempDir.toString();
        List<DdlStatement> ddlStatements = List.of(
                new DdlStatement("Users", "Sales", "tables", "CREATE TABLE Sales.Users (id INT);"),
                new DdlStatement("Orders", "Sales", "tables", "CREATE TABLE Sales.Orders (order_id INT);"),
                new DdlStatement("ActiveUsers", "Sales", "views", "CREATE VIEW Sales.ActiveUsers AS SELECT * FROM Users;")
        );

        // When
        fileWriter.writeDdl(basePath, ddlStatements);

        // Then
        // Check directory structure
        Path salesDir = tempDir.resolve("Sales");
        assertTrue(Files.exists(salesDir));
        Path tablesDir = salesDir.resolve("tables");
        assertTrue(Files.exists(tablesDir));
        Path viewsDir = salesDir.resolve("views");
        assertTrue(Files.exists(viewsDir));

        // Check files exist
        Path usersFile = tablesDir.resolve("Users.sql");
        assertTrue(Files.exists(usersFile));
        Path ordersFile = tablesDir.resolve("Orders.sql");
        assertTrue(Files.exists(ordersFile));
        Path activeUsersFile = viewsDir.resolve("ActiveUsers.sql");
        assertTrue(Files.exists(activeUsersFile));

        // Check file content
        String usersContent = Files.readString(usersFile);
        assertTrue(usersContent.contains("-- Schema: Sales"));
        assertTrue(usersContent.contains("-- Object: Users (tables)"));
        assertTrue(usersContent.contains("CREATE TABLE Sales.Users (id INT);"));

        String ordersContent = Files.readString(ordersFile);
        assertTrue(ordersContent.contains("CREATE TABLE Sales.Orders (order_id INT);"));

        String activeUsersContent = Files.readString(activeUsersFile);
        assertTrue(activeUsersContent.contains("CREATE VIEW Sales.ActiveUsers AS SELECT * FROM Users;"));
    }

    @Test
    @DisplayName("writeDdl should sanitize filenames with problematic characters")
    void writeDdl_shouldSanitizeFilenames() throws IOException {
        // Given
        String basePath = tempDir.toString();
        List<DdlStatement> ddlStatements = List.of(
                new DdlStatement("Table:Name", "Schema/Test", "tables", "CREATE TABLE \"Schema/Test\".\"Table:Name\" (id INT);"),
                new DdlStatement("View*One", "Schema\\Test", "views", "CREATE VIEW \"Schema\\Test\".\"View*One\" AS SELECT 1;")
        );

        // When
        fileWriter.writeDdl(basePath, ddlStatements);

        // Then
        Path schemaTestDir = tempDir.resolve("Schema_Test");
        assertTrue(Files.exists(schemaTestDir));
        Path tablesDir = schemaTestDir.resolve("tables");
        assertTrue(Files.exists(tablesDir));
        Path viewsDir = schemaTestDir.resolve("views");
        assertTrue(Files.exists(viewsDir));

        Path tableFile = tablesDir.resolve("Table_Name.sql");
        assertTrue(Files.exists(tableFile));
        Path viewFile = viewsDir.resolve("View_One.sql");
        assertTrue(Files.exists(viewFile));

        // Verify content includes original schema/object names in comment
        String tableContent = Files.readString(tableFile);
        assertTrue(tableContent.contains("-- Schema: Schema/Test"));
        assertTrue(tableContent.contains("-- Object: Table:Name (tables)"));

        String viewContent = Files.readString(viewFile);
        assertTrue(viewContent.contains("-- Schema: Schema\\Test"));
        assertTrue(viewContent.contains("-- Object: View*One (views)"));
    }

    @Test
    @DisplayName("writeDdl should handle empty list gracefully")
    void writeDdl_whenEmptyList_shouldDoNothing() {
        // Given
        String basePath = tempDir.toString();
        List<DdlStatement> ddlStatements = List.of();

        // When & Then (no exception expected)
        assertDoesNotThrow(() -> fileWriter.writeDdl(basePath, ddlStatements));
        // No directories should be created (except temp dir)
        assertEquals(0, tempDir.toFile().list().length);
    }

    @Test
    @DisplayName("writeDdl should handle null list gracefully")
    void writeDdl_whenNullList_shouldDoNothing() {
        // Given
        String basePath = tempDir.toString();

        // When & Then (no exception expected)
        assertDoesNotThrow(() -> fileWriter.writeDdl(basePath, null));
        // No directories should be created
        assertEquals(0, tempDir.toFile().list().length);
    }

    @Test
    @DisplayName("writeDdl should throw RuntimeException on IO error")
    void writeDdl_whenIOException_shouldThrowRuntimeException() {
        // Given: a base path that is read‑only (or impossible to write)
        // We can use a non‑existent root to force IOException (on Windows maybe C:\nonexistent\folder)
        // However, we cannot rely on that; we'll use a path that is a file instead of a directory
        // Creating a file and then trying to create directories inside will cause IOException
        Path fileAsBase = tempDir.resolve("file.txt");
        try {
            Files.writeString(fileAsBase, "I am a file");
        } catch (IOException e) {
            fail("Setup failed");
        }
        String basePath = fileAsBase.toString();
        List<DdlStatement> ddlStatements = List.of(
                new DdlStatement("Test", "Schema", "tables", "CREATE TABLE Schema.Test (id INT);")
        );

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> fileWriter.writeDdl(basePath, ddlStatements));
        assertTrue(ex.getMessage().contains("Failed to write DDL files"));
    }
}