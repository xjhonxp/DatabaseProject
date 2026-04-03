package pe.openstrategy.databaseproject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * HTTP request DTO for database extraction.
 * Credentials are provided via Basic Authentication header, not in this DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionRequestDto {

    @NotBlank(message = "Database type is required")
    @JsonProperty("dbType")
    private String dbType;

    @NotBlank(message = "Host is required")
    @JsonProperty("host")
    private String host;

    @NotNull(message = "Port is required")
    @Positive(message = "Port must be positive")
    @JsonProperty("port")
    private Integer port;

    @NotBlank(message = "Database name is required")
    @JsonProperty("database")
    private String database;

    @NotBlank(message = "Project directory is required")
    @JsonProperty("projectDir")
    private String projectDir;

    @JsonProperty("schemas")
    private List<String> schemas;

    @JsonProperty("objectTypes")
    private List<String> objectTypes;

    @JsonProperty(value = "extendedProperty", required = false)
    @Pattern(regexp = "^$|^\\S+=.*$", message = "extendedProperty must be empty or in key=value format with no spaces in key")
    private String extendedProperty;

    // Manual getters for compatibility (Lombok may not be recognized by IDE)
    public String getDbType() {
        return dbType;
    }

    public String getHost() {
        return host;
    }

    public Integer getPort() {
        return port;
    }

    public String getDatabase() {
        return database;
    }

    public String getProjectDir() {
        return projectDir;
    }

    public List<String> getSchemas() {
        return schemas;
    }

    public List<String> getObjectTypes() {
        return objectTypes;
    }

    public String getExtendedProperty() {
        return extendedProperty;
    }
}