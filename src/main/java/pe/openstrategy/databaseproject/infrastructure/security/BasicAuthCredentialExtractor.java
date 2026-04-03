package pe.openstrategy.databaseproject.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pe.openstrategy.databaseproject.domain.valueobject.Credential;
import pe.openstrategy.databaseproject.port.out.CredentialExtractor;

import java.util.Base64;

/**
 * Extracts Basic Authentication credentials from Authorization header.
 * Security rules:
 * - Never log the Authorization header at any log level
 * - Never log decoded username or password
 * - Only log generic success/failure messages
 */
@Slf4j
@Component
public class BasicAuthCredentialExtractor implements CredentialExtractor {

    private static final String BASIC_PREFIX = "Basic ";

    @Override
    public Credential extract(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BASIC_PREFIX)) {
            log.debug("Missing or invalid Authorization header (no Basic prefix)");
            throw new IllegalArgumentException("Missing or invalid Authorization header");
        }

        String base64Credentials = authorizationHeader.substring(BASIC_PREFIX.length());
        String credentials;
        try {
            credentials = new String(Base64.getDecoder().decode(base64Credentials));
        } catch (IllegalArgumentException e) {
            log.debug("Failed to decode Base64 credentials");
            throw new IllegalArgumentException("Invalid Base64 encoding in Authorization header", e);
        }

        String[] parts = credentials.split(":", 2);
        if (parts.length != 2) {
            log.debug("Malformed credentials format");
            throw new IllegalArgumentException("Invalid credentials format");
        }

        String username = parts[0];
        String password = parts[1];

        // Validate non-empty
        if (username.isEmpty() || password.isEmpty()) {
            log.debug("Empty username or password");
            throw new IllegalArgumentException("Username and password cannot be empty");
        }

        log.debug("Basic Auth credentials extracted successfully");
        return new Credential(username, password);
    }
}