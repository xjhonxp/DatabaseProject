package pe.openstrategy.databaseproject.domain.valueobject;

/**
 * Represents a DDL (Data Definition Language) statement.
 */
public record DdlStatement(
    String objectName,
    String schema,
    String objectType,
    String ddl
) {
    
    public DdlStatement {
        if (objectName == null || objectName.isBlank()) {
            throw new IllegalArgumentException("Object name cannot be null or blank");
        }
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("Schema cannot be null or blank");
        }
        if (objectType == null || objectType.isBlank()) {
            throw new IllegalArgumentException("Object type cannot be null or blank");
        }
        if (ddl == null || ddl.isBlank()) {
            throw new IllegalArgumentException("DDL cannot be null or blank");
        }
    }
}