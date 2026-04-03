package pe.openstrategy.databaseproject.application.exception;

/**
 * Thrown when an unsupported database type is requested.
 */
public class UnsupportedDatabaseTypeException extends RuntimeException {
    
    public UnsupportedDatabaseTypeException(String message) {
        super(message);
    }
    
    public UnsupportedDatabaseTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}