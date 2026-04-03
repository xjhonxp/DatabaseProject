package pe.openstrategy.databaseproject.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import pe.openstrategy.databaseproject.dto.ExtractionRequestDto;
import pe.openstrategy.databaseproject.dto.ExtractionResponseDto;

/**
 * OpenAPI-annotated interface for database extraction operations.
 * All OpenAPI annotations belong here, not in the controller implementation.
 */
@Tag(name = "Database Extraction", description = "Extract database metadata and generate DDL files")
public interface ExtractionApi {

    @Operation(summary = "Extract database structure", description = "Connects to a database, extracts metadata, and generates DDL files. "
            + "Credentials must be provided via Basic Authentication header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Extraction completed successfully", content = @Content(schema = @Schema(implementation = ExtractionResponseDto.class))),
            @ApiResponse(responseCode = "202", description = "Extraction started asynchronously"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "401", description = "Authentication required or invalid credentials"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    ResponseEntity<ExtractionResponseDto> extractDatabase(
            @Parameter(description = "Extraction request details", required = true) @Valid @RequestBody ExtractionRequestDto requestDto,
            HttpServletRequest httpRequest);
}