package pe.openstrategy.databaseproject.application.exception;

/**
 * Thrown when database connection validation fails.
 */
public class DatabaseConnectionException extends RuntimeException {
    
    public DatabaseConnectionException(String message) {
        super(message);
    }
    
    public DatabaseConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}