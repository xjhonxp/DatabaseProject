package pe.openstrategy.databaseproject.application.usecase;

import pe.openstrategy.databaseproject.domain.DatabaseType;
import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;

import java.util.List;

/**
 * Input for the ExtractDatabaseStructureUseCase.
 */
public record ExtractionRequest(
    DatabaseType dbType,
    DatabaseConnection connection,
    String outputBasePath,
    List<String> schemaFilter,
    List<String> objectTypeFilter
) {
    
    public ExtractionRequest {
        if (dbType == null) {
            throw new IllegalArgumentException("Database type cannot be null");
        }
        if (connection == null) {
            throw new IllegalArgumentException("Database connection cannot be null");
        }
        if (outputBasePath == null || outputBasePath.isBlank()) {
            throw new IllegalArgumentException("Output base path cannot be null or blank");
        }
    }
    
    public boolean hasSchemaFilter() {
        return schemaFilter != null && !schemaFilter.isEmpty();
    }
    
    public boolean hasObjectTypeFilter() {
        return objectTypeFilter != null && !objectTypeFilter.isEmpty();
    }
}