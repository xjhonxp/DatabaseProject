package pe.openstrategy.databaseproject.domain.valueobject;

/**
 * Represents a database schema.
 */
public record Schema(String name) {
    
    public Schema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Schema name cannot be null or blank");
        }
    }
}