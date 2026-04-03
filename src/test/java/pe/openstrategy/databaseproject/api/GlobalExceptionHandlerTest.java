package pe.openstrategy.databaseproject.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import pe.openstrategy.databaseproject.dto.ErrorResponseDto;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(webRequest.getDescription(false)).thenReturn("uri=/api/extract");
    }

    @Test
    @DisplayName("handleIllegalArgumentException should return 400 with error details")
    void handleIllegalArgumentException_shouldReturnBadRequest() {
        // Given
        IllegalArgumentException ex = new IllegalArgumentException("Invalid database type");

        // When
        ResponseEntity<ErrorResponseDto> response = handler.handleIllegalArgumentException(ex, webRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Bad Request", response.getBody().getError());
        assertEquals("Invalid database type", response.getBody().getMessage());
        assertEquals("uri=/api/extract", response.getBody().getPath());
    }

    @Test
    @DisplayName("handleValidationExceptions should return 400 with field errors")
    void handleValidationExceptions_shouldReturnBadRequestWithDetails() {
        // Given
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("object", "dbType", "must not be null");
        FieldError fieldError2 = new FieldError("object", "host", "must be a valid hostname");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        // When
        ResponseEntity<ErrorResponseDto> response = handler.handleValidationExceptions(ex, webRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Validation Failed", response.getBody().getError());
        assertEquals("Invalid request parameters", response.getBody().getMessage());
        assertEquals("uri=/api/extract", response.getBody().getPath());
        assertNotNull(response.getBody().getDetails());
        assertEquals(2, response.getBody().getDetails().size());
        assertTrue(response.getBody().getDetails().contains("must not be null"));
        assertTrue(response.getBody().getDetails().contains("must be a valid hostname"));
    }

    @Test
    @DisplayName("handleConstraintViolationException should return 400 with constraint violations")
    void handleConstraintViolationException_shouldReturnBadRequestWithViolations() {
        // Given
        // Simulate constraint violations using a simple implementation
        // We'll create a dummy Set with a single violation using a mock
        ConstraintViolation<String> violation = mock(ConstraintViolation.class);
        jakarta.validation.Path path = mock(jakarta.validation.Path.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(path.toString()).thenReturn("dbType");
        when(violation.getMessage()).thenReturn("must not be null");
        Set<ConstraintViolation<String>> violations = Set.of(violation);
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        // When
        ResponseEntity<ErrorResponseDto> response = handler.handleConstraintViolationException(ex, webRequest);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
        assertEquals("Constraint Violation", response.getBody().getError());
        assertEquals("Invalid request data", response.getBody().getMessage());
        assertEquals("uri=/api/extract", response.getBody().getPath());
        assertNotNull(response.getBody().getDetails());
        assertEquals(1, response.getBody().getDetails().size());
        assertEquals("dbType: must not be null", response.getBody().getDetails().get(0));
    }

    @Test
    @DisplayName("handleBadCredentialsException should return 401 with generic message")
    void handleBadCredentialsException_shouldReturnUnauthorized() {
        // Given
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        // When
        ResponseEntity<ErrorResponseDto> response = handler.handleBadCredentialsException(ex, webRequest);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Unauthorized", response.getBody().getError());
        assertEquals("Invalid credentials", response.getBody().getMessage());
        assertEquals("uri=/api/extract", response.getBody().getPath());
        assertNull(response.getBody().getDetails());
    }

    @Test
    @DisplayName("handleGenericException should return 500 with generic message")
    void handleGenericException_shouldReturnInternalServerError() {
        // Given
        Exception ex = new RuntimeException("Something went wrong");

        // When
        ResponseEntity<ErrorResponseDto> response = handler.handleGenericException(ex, webRequest);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("Internal Server Error", response.getBody().getError());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
        assertEquals("uri=/api/extract", response.getBody().getPath());
        assertNull(response.getBody().getDetails());
    }

    @Test
    @DisplayName("All handlers should include timestamp")
    void allHandlers_shouldIncludeTimestamp() {
        // Given
        IllegalArgumentException ex = new IllegalArgumentException("test");

        // When
        ResponseEntity<ErrorResponseDto> response = handler.handleIllegalArgumentException(ex, webRequest);

        // Then
        assertNotNull(response.getBody().getTimestamp());
    }
}