package pe.openstrategy.databaseproject.application.exception;

/**
 * Thrown when database metadata extraction fails.
 */
public class ExtractionFailedException extends RuntimeException {
    
    public ExtractionFailedException(String message) {
        super(message);
    }
    
    public ExtractionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}