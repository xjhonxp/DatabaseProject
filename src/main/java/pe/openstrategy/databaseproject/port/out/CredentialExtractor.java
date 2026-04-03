package pe.openstrategy.databaseproject.port.out;

import pe.openstrategy.databaseproject.domain.valueobject.Credential;

/**
 * Port for extracting credentials from an Authorization header.
 */
public interface CredentialExtractor {
    
    Credential extract(String authorizationHeader);
}