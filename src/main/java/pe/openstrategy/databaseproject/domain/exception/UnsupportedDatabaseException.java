package pe.openstrategy.databaseproject.domain.exception;

/**
 * Thrown when an unsupported database type is requested.
 */
public class UnsupportedDatabaseException extends RuntimeException {
    
    public UnsupportedDatabaseException(String message) {
        super(message);
    }
    
    public UnsupportedDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}