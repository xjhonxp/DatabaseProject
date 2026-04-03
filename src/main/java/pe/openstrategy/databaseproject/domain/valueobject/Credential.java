package pe.openstrategy.databaseproject.domain.valueobject;

/**
 * Represents database credentials (username and password).
 */
public record Credential(String username, String password) {
    
    public Credential {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }
    }
}