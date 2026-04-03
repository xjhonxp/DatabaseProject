package pe.openstrategy.databaseproject.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * HTTP response DTO for database extraction.
 * Follows specification from spec-database-extraction.md
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtractionResponseDto {
    
    @NotBlank
    @JsonProperty("jobId")
    private String jobId;
    
    @NotBlank
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("outputPath")
    private String outputPath;
    
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    public ExtractionResponseDto() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ExtractionResponseDto(String jobId, String status, String message, String outputPath) {
        this();
        this.jobId = jobId;
        this.status = status;
        this.message = message;
        this.outputPath = outputPath;
    }
    
    public static ExtractionResponseDto started(String jobId, String message, String outputPath) {
        return new ExtractionResponseDto(jobId, "STARTED", message, outputPath);
    }
    
    public static ExtractionResponseDto completed(String jobId, String message, String outputPath) {
        return new ExtractionResponseDto(jobId, "COMPLETED", message, outputPath);
    }
    
    public static ExtractionResponseDto failed(String jobId, String message, String outputPath) {
        return new ExtractionResponseDto(jobId, "FAILED", message, outputPath);
    }
}