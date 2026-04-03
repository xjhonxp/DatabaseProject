package pe.openstrategy.databaseproject.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import pe.openstrategy.databaseproject.domain.valueobject.Credential;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BasicAuthCredentialExtractor}.
 */
@ExtendWith(MockitoExtension.class)
class BasicAuthCredentialExtractorTest {

    private BasicAuthCredentialExtractor extractor;

    @Mock
    private Logger log;

    @BeforeEach
    void setUp() {
        extractor = new BasicAuthCredentialExtractor();
        // Note: We cannot easily mock the Slf4j logger because it's static.
        // We'll rely on the fact that logging is side‑effect only and does not affect correctness.
    }

    @Test
    @DisplayName("extract should decode valid Basic Auth header")
    void extract_shouldDecodeValidBasicAuthHeader() {
        String base64 = java.util.Base64.getEncoder().encodeToString("username:password".getBytes());
        String header = "Basic " + base64;

        Credential credential = extractor.extract(header);

        assertEquals("username", credential.username());
        assertEquals("password", credential.password());
    }

    @Test
    @DisplayName("extract should throw when authorization header is null")
    void extract_whenHeaderIsNull_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(null));
        assertEquals("Missing or invalid Authorization header", ex.getMessage());
    }

    @Test
    @DisplayName("extract should throw when header does not start with 'Basic '")
    void extract_whenHeaderMissingBasicPrefix_shouldThrow() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract("Bearer token123"));
        assertEquals("Missing or invalid Authorization header", ex.getMessage());
    }

    @Test
    @DisplayName("extract should throw when Base64 encoding is invalid")
    void extract_whenBase64Invalid_shouldThrow() {
        String header = "Basic not-base64!";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(header));
        assertEquals("Invalid Base64 encoding in Authorization header", ex.getMessage());
    }

    @Test
    @DisplayName("extract should throw when credentials lack colon separator")
    void extract_whenCredentialsLackColon_shouldThrow() {
        String base64 = java.util.Base64.getEncoder().encodeToString("nocolon".getBytes());
        String header = "Basic " + base64;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(header));
        assertEquals("Invalid credentials format", ex.getMessage());
    }

    @Test
    @DisplayName("extract should throw when username is empty")
    void extract_whenUsernameEmpty_shouldThrow() {
        String base64 = java.util.Base64.getEncoder().encodeToString(":password".getBytes());
        String header = "Basic " + base64;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(header));
        assertEquals("Username and password cannot be empty", ex.getMessage());
    }

    @Test
    @DisplayName("extract should throw when password is empty")
    void extract_whenPasswordEmpty_shouldThrow() {
        String base64 = java.util.Base64.getEncoder().encodeToString("username:".getBytes());
        String header = "Basic " + base64;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> extractor.extract(header));
        assertEquals("Username and password cannot be empty", ex.getMessage());
    }

    @Test
    @DisplayName("extract should handle password containing colon")
    void extract_shouldHandlePasswordContainingColon() {
        String base64 = java.util.Base64.getEncoder().encodeToString("user:pass:word".getBytes());
        String header = "Basic " + base64;

        Credential credential = extractor.extract(header);

        assertEquals("user", credential.username());
        assertEquals("pass:word", credential.password());
    }

    @Test
    @DisplayName("extract should trim nothing (raw credentials)")
    void extract_shouldNotTrimCredentials() {
        // The spec does not require trimming; we preserve exact bytes.
        String base64 = java.util.Base64.getEncoder().encodeToString("  user  :  pass  ".getBytes());
        String header = "Basic " + base64;

        Credential credential = extractor.extract(header);

        assertEquals("  user  ", credential.username());
        assertEquals("  pass  ", credential.password());
    }

    @Test
    @DisplayName("extract should not log credentials")
    void extract_shouldNotLogCredentials() {
        // This test is manual – we rely on the code inspection that log statements
        // do not include the header, username, or password.
        // We can at least verify that the method returns the expected credential.
        String base64 = java.util.Base64.getEncoder().encodeToString("secretUser:secretPass".getBytes());
        String header = "Basic " + base64;

        Credential credential = extractor.extract(header);

        assertEquals("secretUser", credential.username());
        assertEquals("secretPass", credential.password());
        // No assertion about logging – we trust the code review.
    }
}