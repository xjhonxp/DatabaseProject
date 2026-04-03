package pe.openstrategy.databaseproject.interfaceadapters.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.openstrategy.databaseproject.api.ExtractionApi;
import pe.openstrategy.databaseproject.application.usecase.ExtractionRequest;
import pe.openstrategy.databaseproject.application.usecase.ExtractionResult;
import pe.openstrategy.databaseproject.application.usecase.ExtractDatabaseStructureUseCase;
import pe.openstrategy.databaseproject.domain.DatabaseType;
import pe.openstrategy.databaseproject.domain.valueobject.Credential;
import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;
import pe.openstrategy.databaseproject.infrastructure.database.jdbc.JdbcUrlBuilder;
import pe.openstrategy.databaseproject.dto.ExtractionRequestDto;
import pe.openstrategy.databaseproject.dto.ExtractionResponseDto;
import pe.openstrategy.databaseproject.port.out.CredentialExtractor;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for database extraction operations.
 */
@RestController
@RequestMapping("/api/extract")
@RequiredArgsConstructor
public class DatabaseExtractionController implements ExtractionApi {

    private static final Logger log = LoggerFactory.getLogger(DatabaseExtractionController.class);
    private final ExtractDatabaseStructureUseCase extractDatabaseStructureUseCase;
    private final CredentialExtractor credentialExtractor;

    @Override
    @PostMapping
    public ResponseEntity<ExtractionResponseDto> extractDatabase(
            @Valid @RequestBody ExtractionRequestDto requestDto,
            HttpServletRequest httpRequest) {

        String jobId = UUID.randomUUID().toString();
        log.info("Received extraction request jobId={}, dbType={}, host={}",
                jobId, requestDto.getDbType(), requestDto.getHost());

        // Extract credentials from Basic Auth header
        String authorizationHeader = httpRequest.getHeader("Authorization");
        Credential credential = credentialExtractor.extract(authorizationHeader);
        String username = credential.username();
        String password = credential.password();

        // Convert string dbType to enum
        DatabaseType databaseType = DatabaseType.fromString(requestDto.getDbType());

        // Build JDBC URL using enum
        String jdbcUrl = JdbcUrlBuilder.buildUrl(
                databaseType,
                requestDto.getHost(),
                requestDto.getPort(),
                requestDto.getDatabase());

        // Map to domain request
        ExtractionRequest request = mapToDomainRequest(databaseType, requestDto, jdbcUrl, username, password,
                requestDto.getExtendedProperty());

        // Execute extraction synchronously
        ExtractionResult result = extractDatabaseStructureUseCase.execute(request);

        // Map to response DTO
        ExtractionResponseDto response = mapToResponseDto(jobId, result);

        HttpStatus status = result.success() ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        log.info("Extraction jobId={} completed with status={}", jobId, result.success() ? "SUCCESS" : "FAILED");
        return ResponseEntity.status(status).body(response);
    }

    private ExtractionRequest mapToDomainRequest(DatabaseType databaseType, ExtractionRequestDto dto,
            String jdbcUrl, String username, String password, String extendedProperty) {
        DatabaseConnection connection = new DatabaseConnection(
                jdbcUrl,
                username,
                password,
                databaseType,
                extendedProperty);

        return new ExtractionRequest(
                databaseType,
                connection,
                dto.getProjectDir(),
                dto.getSchemas() != null ? dto.getSchemas() : List.of(),
                dto.getObjectTypes() != null ? dto.getObjectTypes() : List.of());
    }

    private ExtractionResponseDto mapToResponseDto(String jobId, ExtractionResult result) {
        if (result.success()) {
            return ExtractionResponseDto.completed(jobId, result.message(), result.outputPath());
        } else {
            return ExtractionResponseDto.failed(jobId, result.message(), result.outputPath());
        }
    }
}