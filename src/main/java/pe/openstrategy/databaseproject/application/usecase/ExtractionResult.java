package pe.openstrategy.databaseproject.application.usecase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Output from the ExtractDatabaseStructureUseCase.
 */
public record ExtractionResult(
    boolean success,
    String message,
    String outputPath,
    LocalDateTime timestamp,
    Map<String, Integer> objectCounts,
    List<String> errors
) {
    
    public ExtractionResult(boolean success, String message, String outputPath,
                            Map<String, Integer> objectCounts) {
        this(success, message, outputPath, LocalDateTime.now(), objectCounts, List.of());
    }
    
    public ExtractionResult withErrors(List<String> errors) {
        return new ExtractionResult(success, message, outputPath, timestamp, objectCounts, errors);
    }
}