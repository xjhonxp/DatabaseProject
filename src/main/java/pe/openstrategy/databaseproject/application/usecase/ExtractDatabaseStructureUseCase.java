package pe.openstrategy.databaseproject.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.openstrategy.databaseproject.application.exception.DatabaseConnectionException;
import pe.openstrategy.databaseproject.application.exception.ExtractionFailedException;
import pe.openstrategy.databaseproject.application.exception.FileWriteException;
import pe.openstrategy.databaseproject.application.exception.UnsupportedDatabaseTypeException;
import pe.openstrategy.databaseproject.domain.DatabaseType;
import pe.openstrategy.databaseproject.domain.DdlObjectType;
import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;
import pe.openstrategy.databaseproject.domain.valueobject.DdlStatement;
import pe.openstrategy.databaseproject.domain.valueobject.Schema;
import pe.openstrategy.databaseproject.port.in.ExtractionInputPort;
import pe.openstrategy.databaseproject.port.out.DdlExtractor;
import pe.openstrategy.databaseproject.port.out.DatabaseConnectionValidator;
import pe.openstrategy.databaseproject.port.out.FileWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of the extraction use case.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExtractDatabaseStructureUseCase implements ExtractionInputPort {

    private final Map<DatabaseType, DdlExtractor> extractorRegistry;
    private final DatabaseConnectionValidator connectionValidator;
    private final FileWriter fileWriter;

    @Override
    public ExtractionResult execute(ExtractionRequest request) {
        log.info("Starting database extraction for {}", request.connection().jdbcUrl());

        try {
            validateConnection(request);
            DdlExtractor extractor = resolveExtractor(request.dbType());
            List<Schema> schemas = extractAndFilterSchemas(request, extractor);
            log.debug("Esquemas obtenidos : {}", schemas.size());
            List<DdlStatement> ddlStatements = extractObjectsForSchemas(request, schemas, extractor);
            fileWriter.writeDdl(request.outputBasePath(), ddlStatements);

            Map<String, Integer> objectCounts = countByObjectType(ddlStatements);
            log.info("Database extraction completed successfully. Objects extracted: {}", objectCounts);
            return new ExtractionResult(true, "Extraction completed successfully",
                    request.outputBasePath(), objectCounts);

        } catch (UnsupportedDatabaseTypeException | DatabaseConnectionException | ExtractionFailedException
                | FileWriteException e) {
            // Re-throw specific exceptions – they will be handled by global exception
            // handler
            throw e;
        } catch (Exception e) {
            // Wrap any unexpected exception as ExtractionFailedException
            throw new ExtractionFailedException("Unexpected error during extraction", e);
        }
    }

    private void validateConnection(ExtractionRequest request) {
        if (!connectionValidator.isValid(request.connection())) {
            throw new DatabaseConnectionException("Database connection validation failed");
        }
    }

    private DdlExtractor resolveExtractor(DatabaseType databaseType) {
        DdlExtractor extractor = extractorRegistry.get(databaseType);
        if (extractor == null) {
            throw new UnsupportedDatabaseTypeException("Unsupported database type: " + databaseType);
        }
        return extractor;
    }

    private List<Schema> extractAndFilterSchemas(ExtractionRequest request, DdlExtractor extractor) {
        List<Schema> schemas = extractor.extractSchemas(request.connection());
        if (request.hasSchemaFilter()) {
            Set<String> filterSet = request.schemaFilter().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
            schemas = schemas.stream()
                    .filter(schema -> filterSet.contains(schema.name().toLowerCase()))
                    .toList();
        }
        if (schemas.isEmpty()) {
            throw new ExtractionFailedException("No schemas found matching criteria");
        }
        return schemas;
    }

    private List<DdlStatement> extractObjectsForSchemas(ExtractionRequest request,
            List<Schema> schemas,
            DdlExtractor extractor) {
        List<DdlStatement> allDdlStatements = new ArrayList<>();
        for (Schema schema : schemas) {
            log.debug("Processing schema: {}", schema.name());
            allDdlStatements.addAll(extractTables(request, schema, extractor));
            allDdlStatements.addAll(extractViews(request, schema, extractor));
            allDdlStatements.addAll(extractIndexes(request, schema, extractor));
            allDdlStatements.addAll(extractStoredProcedures(request, schema, extractor));
            allDdlStatements.addAll(extractFunctions(request, schema, extractor));
            allDdlStatements.addAll(extractSequences(request, schema, extractor));
        }
        return allDdlStatements;
    }

    private List<DdlStatement> extractTables(ExtractionRequest request, Schema schema,
            DdlExtractor extractor) {
        if (shouldExtractObjectType(request, DdlObjectType.TABLE)) {
            return extractor.extractTables(schema, request.connection());
        }
        return List.of();
    }

    private List<DdlStatement> extractViews(ExtractionRequest request, Schema schema,
            DdlExtractor extractor) {
        if (shouldExtractObjectType(request, DdlObjectType.VIEW)) {
            return extractor.extractViews(schema, request.connection());
        }
        return List.of();
    }

    private List<DdlStatement> extractIndexes(ExtractionRequest request, Schema schema,
            DdlExtractor extractor) {
        if (shouldExtractObjectType(request, DdlObjectType.INDEX)) {
            return extractor.extractIndexes(schema, request.connection());
        }
        return List.of();
    }

    private List<DdlStatement> extractStoredProcedures(ExtractionRequest request, Schema schema,
            DdlExtractor extractor) {
        if (shouldExtractObjectType(request, DdlObjectType.PROCEDURE)) {
            return extractor.extractStoredProcedures(schema, request.connection());
        }
        return List.of();
    }

    private List<DdlStatement> extractFunctions(ExtractionRequest request, Schema schema,
            DdlExtractor extractor) {
        if (shouldExtractObjectType(request, DdlObjectType.FUNCTION)) {
            return extractor.extractFunctions(schema, request.connection());
        }
        return List.of();
    }

    private List<DdlStatement> extractSequences(ExtractionRequest request, Schema schema,
            DdlExtractor extractor) {
        if (shouldExtractObjectType(request, DdlObjectType.SEQUENCE)) {
            return extractor.extractSequences(schema, request.connection());
        }
        return List.of();
    }

    private boolean shouldExtractObjectType(ExtractionRequest request, DdlObjectType targetType) {
        if (!request.hasObjectTypeFilter()) {
            return true;
        }
        // Convert filter strings to DdlObjectType ignoring case
        Set<DdlObjectType> filterSet = request.objectTypeFilter().stream()
                .map(String::toLowerCase)
                .map(filter -> {
                    try {
                        return DdlObjectType.fromString(filter);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        return filterSet.contains(targetType);
    }

    private Map<String, Integer> countByObjectType(List<DdlStatement> ddlStatements) {
        Map<String, Integer> counts = new HashMap<>();
        for (DdlStatement ddl : ddlStatements) {
            counts.merge(ddl.objectType(), 1, Integer::sum);
        }
        return counts;
    }
}