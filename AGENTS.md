# AGENTS.md

<!--
  ROLE OF THIS FILE
  -----------------
  This file defines HOW to think and write code in this project.
  It does NOT define what to build — that is in docs/project.md.
  It does NOT define execution order — that is in docs/flow.md.

  Source of truth hierarchy:
    1. docs/project.md   — requirements, contracts, supported engines
    2. docs/flow.md      — step-by-step execution workflow
    3. AGENTS.md         — coding standards and architectural rules (this file)

  When there is a conflict, docs/project.md wins.
-->

---

## 1. Before writing any code

Read `docs/project.md` and `docs/flow.md` in full before producing any output.
Follow `docs/flow.md` step by step. Do not merge steps. Do not skip steps.
Do not write implementation code before steps 1–4 (clarification, planning,
specification, architecture) are complete.

If any requirement in `docs/project.md` is ambiguous, stop and ask.
State the ambiguity explicitly. Do not assume and proceed silently.

---

## 2. Package structure

Every class belongs to exactly one of these layers. Never mix concerns across layers.

```
com.example.ddlextractor
├── api                        # OpenAPI-annotated interfaces only
│   └── ExtractionApi.java
├── controller                 # Implements api interfaces — no logic
│   └── ExtractionController.java
├── application                # Use cases — orchestrates ports, no framework deps
│   └── usecase
│       └── ExtractDatabaseStructureUseCase.java
├── domain                     # Enums, value objects — zero Spring dependencies
│   ├── DatabaseType.java
│   └── DdlObjectType.java
├── port                       # Interfaces used by application layer
│   ├── out
│   │   ├── DdlExtractor.java
│   │   ├── FileWriter.java
│   │   └── CredentialExtractor.java
│   └── in
│       └── ExtractionInputPort.java
├── infrastructure
│   ├── jdbc
│   │   ├── JdbcConnectionFactory.java
│   │   └── JdbcTemplate.java
│   ├── extractor              # One class per supported database engine
│   │   ├── PostgresDdlExtractor.java
│   │   ├── OracleDdlExtractor.java
│   │   ├── SqlServerDdlExtractor.java   # Handles both SQL_SERVER and AZURE_SQL
│   │   └── MariaDbDdlExtractor.java
│   ├── filesystem
│   │   └── LocalFileWriter.java
│   └── security
│       └── BasicAuthCredentialExtractor.java
├── dto                        # Request and response objects — no logic
│   ├── ExtractionRequestDto.java
│   └── ExtractionResponseDto.java
└── config
    ├── ExtractorRegistryConfig.java   # Map<DatabaseType, DdlExtractor> bean
    └── SecurityConfig.java
```

**Dependency rule (strictly enforced):**
`api` → `controller` → `application` → `port` ← `infrastructure`
`domain` has no outward dependencies. `application` depends only on `port` interfaces.
`infrastructure` implements `port` interfaces. Nothing in `application` or `domain`
may import from `infrastructure` or `org.springframework`.

---

## 3. Layer responsibilities

### domain
- Enums and value objects only.
- No Spring annotations. No Jackson annotations. No JDBC imports.
- Must compile without Spring on the classpath.

### application (use cases)
- Depends only on interfaces from `port`.
- Orchestrates the extraction flow: validate → connect → extract → write files.
- Never references a concrete infrastructure class directly.
- Never catches generic `Exception` — define specific checked or runtime exceptions.

### port
- Plain Java interfaces. No annotations.
- Method signatures use domain types and primitives only.
- No DTO types cross the port boundary — map at the controller or use-case level.

### infrastructure
- Implements port interfaces.
- All JDBC work lives here. No other layer touches JDBC types.
- `JdbcConnectionFactory` is the only class allowed to call `DriverManager`
  or create a `DataSource`. Every other class receives a `Connection` from it.
- `JdbcTemplate` is the only class allowed to reference `Connection`,
  `PreparedStatement`, and `ResultSet` directly.

### api (interface layer)
- Contains only the `ExtractionApi` interface with all OpenAPI annotations.
- No implementation code. No logic.

### controller
- Implements the `ExtractionApi` interface.
- Contains only delegation to the input port. No business logic.
- Maps DTOs to domain types before calling the use case.

---

## 4. Coding standards

### 4.1 Lombok — required usage

| Class type                          | Required annotation(s)                      |
|-------------------------------------|---------------------------------------------|
| Spring service, use case, config    | `@RequiredArgsConstructor`                  |
| DTO (request / response)            | `@NoArgsConstructor` + `@AllArgsConstructor` + `@Data` or `@Value` |
| Immutable value object              | `@Value`                                    |
| Domain enum                         | Manual constructor (enums need field init)  |

`@Autowired` on fields or constructors is **forbidden** in all classes.

Manual constructors are allowed only when:
- The object must enforce invariants at construction time (null checks, range validation).
- The class is an enum (Lombok does not support enum constructors).

```java
// CORRECT — Spring service
@Service
@RequiredArgsConstructor
public class ExtractDatabaseStructureUseCase implements ExtractionInputPort {
    private final DdlExtractor extractor;
    private final FileWriter fileWriter;
}

// WRONG — field injection
@Service
public class ExtractDatabaseStructureUseCase {
    @Autowired
    private DdlExtractor extractor;
}
```
> **Why `@RequiredArgsConstructor` and not `@Autowired`**
>
> Both approaches tell Spring to inject dependencies automatically — the runtime
> behavior is identical. The difference matters in unit tests.
>
> `@RequiredArgsConstructor` (Lombok) generates a constructor from all `final`
> fields. Spring detects it and uses it for injection. You never write the
> constructor manually. In unit tests, you instantiate the class directly without
> Spring context:
>
> ```java
> // Clean, explicit, no reflection — works with MockitoExtension
> useCase = new ExtractDatabaseStructureUseCase(mockExtractor, mockFileWriter);
> ```
>
> `@Autowired` on fields requires Spring context or Mockito reflection (`@InjectMocks`)
> to inject dependencies in tests. When Mockito cannot resolve a dependency via
> reflection it does not throw an error — it silently leaves the field as `null`,
> causing `NullPointerException` at runtime with no clear explanation.
>
> This is why `@Autowired` is forbidden in this project: it makes failures silent
> and tests harder to write correctly.

### 4.2 OpenAPI annotations

OpenAPI annotations belong in the `api` interface and in DTOs — nowhere else.
Controllers must not contain `@Operation`, `@ApiResponse`, `@Tag`, or `@Parameter`.

```java
// CORRECT — annotation in interface
public interface ExtractionApi {
    @Operation(summary = "Extract DDL from a connected database")
    @ApiResponse(responseCode = "200",
        content = @Content(schema = @Schema(implementation = ExtractionResponseDto.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "502", description = "Connection failure")
    ResponseEntity<ExtractionResponseDto> extract(@RequestBody @Valid ExtractionRequestDto request);
}

// CORRECT — controller is clean
@RestController
@RequiredArgsConstructor
public class ExtractionController implements ExtractionApi {
    private final ExtractionInputPort extractionInputPort;

    @Override
    public ResponseEntity<ExtractionResponseDto> extract(ExtractionRequestDto request) {
        return ResponseEntity.ok(extractionInputPort.execute(request));
    }
}
```

### 4.3 DatabaseType enum — required pattern

Every database engine is a `DatabaseType` entry. No string comparisons for engine
resolution anywhere in the codebase. No `switch` on raw strings. No `equals`/`contains`
on engine name strings.

```java
// CORRECT
public enum DatabaseType {
    POSTGRESQL("org.postgresql.Driver", 5432, "postgresql"),
    ORACLE("oracle.jdbc.OracleDriver", 1521, "oracle"),
    SQL_SERVER("com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433, "sqlserver"),
    AZURE_SQL("com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433, "sqlserver"),
    MARIADB("org.mariadb.jdbc.Driver", 3306, "mariadb");

    private final String driverClass;
    private final int defaultPort;
    private final String dialect;

    DatabaseType(String driverClass, int defaultPort, String dialect) {
        this.driverClass = driverClass;
        this.defaultPort = defaultPort;
        this.dialect = dialect;
    }

    // getters
}

// WRONG — string comparison in service
if (request.getDbType().equalsIgnoreCase("postgresql")) { ... }
if (request.getDbType().contains("azure")) { ... }
```

### 4.4 Extractor strategy registry

Engine resolution must use a `Map<DatabaseType, DdlExtractor>` registered as a
Spring bean. The use case receives this map via constructor injection and looks
up the correct extractor by `DatabaseType`. It never contains `if/else` or `switch`
for engine selection.

```java
// CORRECT — registry in config class
@Configuration
public class ExtractorRegistryConfig {

    @Bean
    public Map<DatabaseType, DdlExtractor> extractorRegistry(
            PostgresDdlExtractor postgres,
            OracleDdlExtractor oracle,
            SqlServerDdlExtractor sqlServer,
            MariaDbDdlExtractor mariaDb) {

        return Map.of(
            DatabaseType.POSTGRESQL, postgres,
            DatabaseType.ORACLE,     oracle,
            DatabaseType.SQL_SERVER, sqlServer,
            DatabaseType.AZURE_SQL,  sqlServer,   // shared implementation
            DatabaseType.MARIADB,    mariaDb
        );
    }
}

// CORRECT — use case resolves extractor without branching
DdlExtractor extractor = extractorRegistry.get(databaseType);
if (extractor == null) {
    throw new UnsupportedDatabaseTypeException(databaseType);
}
```

### 4.5 JDBC encapsulation

`JdbcConnectionFactory` is the only class that creates connections.
`JdbcTemplate` is the only class that opens `PreparedStatement` or reads `ResultSet`.
Use `ResultSetExtractor<T>` (functional interface) for all result mapping.

```java
// CORRECT — centralized template
public <T> T query(Connection connection, String sql, ResultSetExtractor<T> extractor) {
    try (PreparedStatement ps = connection.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
        return extractor.extract(rs);
    }
}

// WRONG — JDBC in a DdlExtractor
Connection conn = DriverManager.getConnection(url, user, pass);
PreparedStatement ps = conn.prepareStatement(sql);
ResultSet rs = ps.executeQuery();
```

### 4.6 Security — non-negotiable rules

These rules have no exceptions:

- Never log the `Authorization` header at any log level (DEBUG, TRACE, INFO, WARN, ERROR).
- Never log decoded database credentials (username or password).
- Never include driver error messages, SQL errors, stack traces, or credentials
  in any HTTP response body.
- Sanitize all user-supplied identifiers (schema names, table names, object names)
  before interpolating into SQL strings. Use allowlist validation or
  `DatabaseMetaData` APIs rather than string interpolation where possible.
- Validate all inputs at the DTO layer with Bean Validation before they reach
  the use case.

```java
// CORRECT — secure credential extraction
public Credential extract(HttpServletRequest request) {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.startsWith("Basic ")) {
        throw new MissingCredentialsException();
    }
    // decode and return — never log header or decoded values
    String decoded = new String(Base64.getDecoder().decode(header.substring(6)));
    String[] parts = decoded.split(":", 2);
    return new Credential(parts[0], parts[1]);
}

// WRONG
log.debug("Authorization header: {}", header);
log.info("Connecting with user: {}", username);
```

---

## 5. Error handling

- Define specific exception classes in the `application` layer for each failure mode:
  `UnsupportedDatabaseTypeException`, `DatabaseConnectionException`,
  `ExtractionFailedException`, `FileWriteException`.
- Use a `@RestControllerAdvice` in the `api` or `controller` layer to map these
  exceptions to the response contracts defined in `docs/project.md`.
- Never expose `Exception.getMessage()` from driver or filesystem errors directly
  in responses — log the detail server-side, return a generic client message.
- Never catch generic `Exception` or `Throwable` in use case or service code.

```java
// CORRECT — global handler
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DatabaseConnectionException.class)
    public ResponseEntity<ExtractionResponseDto> handleConnectionError(DatabaseConnectionException ex) {
        log.error("Database connection failed", ex);   // detail in logs only
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(ExtractionResponseDto.connectionError());
    }
}
```

---

## 6. Testing standards

### Unit tests
- Use JUnit 5 + Mockito. No Spring context (`@ExtendWith(MockitoExtension.class)`).
- Mock all port interfaces — never use real JDBC connections in unit tests.
- Test class name: `<ClassName>Test`.
- Test method name: `<methodName>_<scenario>_<expectedOutcome>`.

```java
// Correct naming
@Test
void execute_whenDatabaseTypeIsUnsupported_throwsUnsupportedDatabaseTypeException() { ... }

@Test
void extract_whenAuthorizationHeaderIsMissing_throwsMissingCredentialsException() { ... }
```

### Integration tests
- Use `@SpringBootTest` + Testcontainers for real database connectivity.
- Provide containers for PostgreSQL and MariaDB at minimum.
- Test the full HTTP stack: send a real POST request, verify files are written,
  verify response structure matches `docs/project.md`.
- Security assertions are mandatory:
  - Assert the `Authorization` header does not appear in any log output.
  - Assert credentials do not appear in any response body.

---

## 7. What not to generate

- Do not generate code before steps 1–4 in `docs/flow.md` are complete.
- Do not create utility classes named `Helper`, `Manager`, `Utils`, or `Processor`
  — name every class by what it specifically does.
- Do not use `var` for complex generic types — prefer explicit types for readability.
- Do not add `TODO` comments to generated code — if something is incomplete,
  state it explicitly before the code block, not inside it.
- Do not generate stub or placeholder implementations that silently return empty
  results — throw `UnsupportedOperationException` with a clear message if a
  method is intentionally left for a later step.
- Do not stop at scaffolding. Every step must produce runnable, compilable output.