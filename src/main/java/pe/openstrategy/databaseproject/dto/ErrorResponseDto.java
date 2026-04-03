package pe.openstrategy.databaseproject.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard error response DTO.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponseDto {
    
    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    @JsonProperty("status")
    private int status;
    
    @JsonProperty("error")
    private String error;
    
    @JsonProperty("message")
    private String message;
    
    @JsonProperty("path")
    private String path;
    
    @JsonProperty("details")
    private List<String> details;
    
    public ErrorResponseDto() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ErrorResponseDto(int status, String error, String message, String path) {
        this();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }
    
    public ErrorResponseDto(int status, String error, String message, String path,
                           List<String> details) {
        this(status, error, message, path);
        this.details = details;
    }
}