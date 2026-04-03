package pe.openstrategy.databaseproject.port.out;

import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;
import pe.openstrategy.databaseproject.domain.valueobject.DdlStatement;
import pe.openstrategy.databaseproject.domain.valueobject.Schema;

import java.util.List;

/**
 * Port for extracting DDL statements from a database engine.
 */
public interface DdlExtractor {
    
    List<Schema> extractSchemas(DatabaseConnection connection);
    
    List<DdlStatement> extractTables(Schema schema, DatabaseConnection connection);
    
    List<DdlStatement> extractViews(Schema schema, DatabaseConnection connection);
    
    List<DdlStatement> extractIndexes(Schema schema, DatabaseConnection connection);
    
    List<DdlStatement> extractStoredProcedures(Schema schema, DatabaseConnection connection);
    
    List<DdlStatement> extractFunctions(Schema schema, DatabaseConnection connection);
    
    List<DdlStatement> extractSequences(Schema schema, DatabaseConnection connection);
}