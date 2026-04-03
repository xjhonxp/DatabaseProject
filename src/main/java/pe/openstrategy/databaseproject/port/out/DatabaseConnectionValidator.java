package pe.openstrategy.databaseproject.port.out;

import pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection;

/**
 * Port for validating database connectivity.
 */
public interface DatabaseConnectionValidator {
    
    boolean isValid(DatabaseConnection connection);
}