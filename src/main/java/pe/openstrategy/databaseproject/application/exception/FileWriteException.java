package pe.openstrategy.databaseproject.application.exception;

/**
 * Thrown when writing DDL files fails.
 */
public class FileWriteException extends RuntimeException {
    
    public FileWriteException(String message) {
        super(message);
    }
    
    public FileWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}