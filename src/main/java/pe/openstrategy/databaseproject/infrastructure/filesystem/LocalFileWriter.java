package pe.openstrategy.databaseproject.infrastructure.filesystem;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.openstrategy.databaseproject.port.out.FileWriter;
import pe.openstrategy.databaseproject.domain.valueobject.DdlStatement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Writes DDL statements to the local file system.
 */
@Slf4j
@Component
public class LocalFileWriter implements FileWriter {

    @Override
    public void writeDdl(String basePath, List<DdlStatement> ddlStatements) {
        if (ddlStatements == null || ddlStatements.isEmpty()) {
            log.warn("No DDL statements to write");
            return;
        }
        
        Path outputPath = Paths.get(basePath);
        try {
            Files.createDirectories(outputPath);
            log.info("Writing {} DDL statements to directory: {}", ddlStatements.size(), outputPath.toAbsolutePath());
            
            for (DdlStatement ddl : ddlStatements) {
                writeDdlStatement(outputPath, ddl);
            }
            
            log.info("DDL files written successfully to {}", outputPath.toAbsolutePath());
            
        } catch (IOException e) {
            log.error("Failed to write DDL files to {}", outputPath.toAbsolutePath(), e);
            throw new RuntimeException("Failed to write DDL files: " + e.getMessage(), e);
        }
    }
    
    private void writeDdlStatement(Path basePath, DdlStatement ddl) throws IOException {
        // Create directory structure: {basePath}/{schema}/{objectType}/
        Path schemaDir = basePath.resolve(sanitizeFilename(ddl.schema()));
        Path objectTypeDir = schemaDir.resolve(sanitizeFilename(ddl.objectType().toLowerCase()));
        Files.createDirectories(objectTypeDir);
        
        // Create filename: {objectName}.sql
        String filename = sanitizeFilename(ddl.objectName()) + ".sql";
        Path filePath = objectTypeDir.resolve(filename);
        
        // Append schema name as a comment at the top
        String content = String.format("-- Schema: %s%n-- Object: %s (%s)%n%n%s%n",
            ddl.schema(), ddl.objectName(), ddl.objectType(), ddl.ddl());
        
        Files.writeString(filePath, content);
        log.debug("Written DDL for {}.{} to {}", ddl.schema(), ddl.objectName(), filePath);
    }
    
    private String sanitizeFilename(String name) {
        // Replace characters that are problematic in filenames
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}