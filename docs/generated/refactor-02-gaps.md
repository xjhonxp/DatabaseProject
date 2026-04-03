# Refactor Phase 2 — Gap Analysis

**Based on mapping from:** `docs/generated/refactor-01-mapping.md`  
**Target reference:** AGENTS.md (package structure, coding standards, security rules)

## 1. Classes that map cleanly to target structure

These classes exist and can be moved to the target package with minimal or no changes:

| Current Class | Target Package | Action | Notes |
|---------------|----------------|--------|-------|
| `pe.openstrategy.databaseproject.domain.valueobject.DdlStatement` | `domain.valueobject` | MOVE | Already a value object, no framework dependencies |
| `pe.openstrategy.databaseproject.domain.valueobject.Schema` | `domain.valueobject` | MOVE | Already a value object, no framework dependencies |
| `pe.openstrategy.databaseproject.application.port.DatabaseConnectionValidator` | `port.out.DatabaseConnectionValidator` | MOVE | Already a port interface, no annotations |
| `pe.openstrategy.databaseproject.infrastructure.filesystem.LocalFileSystemWriter` | `infrastructure.filesystem.LocalFileWriter` | RENAME + MOVE | Implements DdlFileWriter (should be FileWriter) |
| `pe.openstrategy.databaseproject.infrastructure.config.SecurityConfig` | `config.SecurityConfig` | MOVE | Already a Spring Security config, needs BasicAuthCredentialExtractor bean |
| `pe.openstrategy.databaseproject.interfaceadapters.web.dto.ExtractionRequestDto` | `dto.ExtractionRequestDto` | MOVE | DTO with validation annotations |
| `pe.openstrategy.databaseproject.interfaceadapters.web.dto.ExtractionResponseDto` | `dto.ExtractionResponseDto` | MOVE | DTO with JSON annotations |
| `pe.openstrategy.databaseproject.interfaceadapters.web.dto.ErrorResponseDto` | `dto.ErrorResponseDto` | MOVE | DTO for error responses |
| `pe.openstrategy.databaseproject.interfaceadapters.web.exception.GlobalExceptionHandler` | `api.GlobalExceptionHandler` | MOVE | Already a `@RestControllerAdvice`, needs to map custom exceptions |

## 2. Classes that must be split (Single Responsibility violations)

None identified. All classes have a single, clear responsibility.

## 3. Classes that must be rewritten (violate AGENTS.md constraints)

These classes exist but violate architectural or coding rules and need substantial changes:

| Current Class | Violations | Required Changes |
|---------------|------------|------------------|
| `pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection` | Uses `String dbType` instead of `DatabaseType` enum | Replace `String dbType` with `DatabaseType` enum field; update constructor validation |
| `pe.openstrategy.databaseproject.application.usercase.ExtractDatabaseStructureUseCase` | Interface should be `ExtractionInputPort` in `port.in` | Rename to `ExtractionInputPort`, move to `port.in` package |
| `pe.openstrategy.databaseproject.application.usercase.ExtractDatabaseStructureService` | 1. Depends on `DatabaseMetadataExtractorResolver` (infrastructure) <br> 2. Catches generic `Exception` <br> 3. Uses string `dbType` instead of `DatabaseType` <br> 4. Does not inject `Map<DatabaseType, DdlExtractor>` | 1. Inject `Map<DatabaseType, DdlExtractor>` registry <br> 2. Throw specific domain exceptions <br> 3. Use `DatabaseType` enum for engine resolution <br> 4. Remove infrastructure dependencies |
| `pe.openstrategy.databaseproject.application.dto.ExtractionRequest` | Uses `String dbType` instead of `DatabaseType` enum | Replace with `DatabaseType` enum; move to `application.usecase` package |
| `pe.openstrategy.databaseproject.application.port.DatabaseMetadataExtractor` | Should be named `DdlExtractor` per AGENTS.md naming | Rename interface to `DdlExtractor`, move to `port.out` |
| `pe.openstrategy.databaseproject.application.port.DdlFileWriter` | Should be named `FileWriter` per AGENTS.md naming | Rename interface to `FileWriter`, move to `port.out` |
| All database extractor implementations (`OracleMetadataExtractor`, `SqlServerMetadataExtractor`, `MariaDbMetadataExtractor`, `PostgreSQLMetadataExtractor`) | 1. Direct `DriverManager` usage (violates JDBC encapsulation) <br> 2. Implement `DatabaseMetadataExtractor` instead of `DdlExtractor` <br> 3. Wrong package location (`infrastructure.database.*`) | 1. Implement `DdlExtractor` interface <br> 2. Use `JdbcConnectionFactory` and `JdbcTemplate` <br> 3. Move to `infrastructure.extractor` package <br> 4. Rename to `OracleDdlExtractor`, etc. |
| `pe.openstrategy.databaseproject.infrastructure.database.azuresql.AzureSqlMetadataExtractor` | Separate class for Azure SQL, should share implementation with SQL Server per AGENTS.md | Delete class; ensure `SqlServerDdlExtractor` handles both `SQL_SERVER` and `AZURE_SQL` enum entries |
| `pe.openstrategy.databaseproject.infrastructure.database.DatabaseMetadataExtractorResolver` | Uses string‑keyed map, violates `DatabaseType` enum resolution rule | Delete; replace with `ExtractorRegistryConfig` providing `Map<DatabaseType, DdlExtractor>` |
| `pe.openstrategy.databaseproject.infrastructure.database.jdbc.JdbcUrlBuilder` | Uses string comparisons for database type (`SUPPORTED_DB_TYPES.contains(...)`) | Rewrite to use `DatabaseType` enum; integrate with `JdbcConnectionFactory` |
| `pe.openstrategy.databaseproject.infrastructure.database.jdbc.JdbcConnectionValidator` | Direct `DriverManager` usage (violates JDBC encapsulation) | Use `JdbcConnectionFactory` to obtain connections |
| `pe.openstrategy.databaseproject.infrastructure.config.DatabaseProjectConfig` | Uses string‑based extractor map, wrong resolver pattern | Split into `ExtractorRegistryConfig` (with `Map<DatabaseType, DdlExtractor>`) and keep `SecurityConfig` |
| `pe.openstrategy.databaseproject.interfaceadapters.web.controller.DatabaseExtractionController` | 1. `@Autowired` constructor injection <br> 2. OpenAPI annotations in controller <br> 3. Business logic (credential extraction, JDBC URL building) <br> 4. Direct infrastructure dependency (`JdbcUrlBuilder`) | 1. Replace `@Autowired` with `@RequiredArgsConstructor` <br> 2. Move OpenAPI annotations to `ExtractionApi` interface <br> 3. Delegate credential extraction to `CredentialExtractor` port <br> 4. Move JDBC URL building to `JdbcConnectionFactory` <br> 5. Implement `ExtractionApi` interface |
| `pe.openstrategy.databaseproject.interfaceadapters.web.security.BasicAuthExtractor` | 1. Static utility, should implement `CredentialExtractor` port <br> 2. Logs username (violates “never log decoded credentials”) <br> 3. Wrong package location | 1. Create `CredentialExtractor` port interface <br> 2. Implement as non‑static class `BasicAuthCredentialExtractor` in `infrastructure.security` <br> 3. Remove any logging of credentials or Authorization header |

## 4. Classes that are missing entirely and must be created

| Class | Target Package | Purpose |
|-------|----------------|---------|
| `DatabaseType` enum | `domain` | Enumeration of supported database engines with driverClass, defaultPort, dialect fields. Must include POSTGRESQL, ORACLE, SQL_SERVER, AZURE_SQL, MARIADB. |
| `DdlObjectType` enum | `domain` | Enumeration of DDL object types: TABLE, VIEW, INDEX, PROCEDURE, FUNCTION, SEQUENCE. |
| `ExtractionApi` interface | `api` | OpenAPI‑annotated interface defining the REST endpoint (`POST /api/extract`). All `@Operation`, `@ApiResponse`, `@Tag`, `@Parameter` annotations belong here. |
| `JdbcConnectionFactory` | `infrastructure.jdbc` | Sole class allowed to call `DriverManager` or create a `DataSource`. Must apply `extendedProperty` via `java.util.Properties` using `split("=", 2)`. Must never log the `extendedProperty` value. |
| `JdbcTemplate` | `infrastructure.jdbc` | Sole class allowed to reference `Connection`, `PreparedStatement`, and `ResultSet` directly. Must accept a `ResultSetExtractor<T>` functional interface for result mapping. |
| `CredentialExtractor` port | `port.out` | Interface for extracting credentials from an HTTP request. |
| `BasicAuthCredentialExtractor` | `infrastructure.security` | Implementation of `CredentialExtractor` that extracts Basic Authentication credentials. Must never log the Authorization header or decoded credentials. |
| `ExtractorRegistryConfig` | `config` | Spring configuration providing a `Map<DatabaseType, DdlExtractor>` bean that wires all five engine implementations. AZURE_SQL and SQL_SERVER must map to the same `SqlServerDdlExtractor` bean. |

## 5. Violations already present in the codebase

### 5.1 `@Autowired` usage
- **Location**: `DatabaseExtractionController.java:42`
- **Violation**: `@Autowired` on constructor (forbidden per AGENTS.md §4.1)
- **Fix**: Replace with `@RequiredArgsConstructor`

### 5.2 OpenAPI annotations in controller
- **Location**: `DatabaseExtractionController.java` (lines 35, 48–59)
- **Violation**: `@Tag`, `@Operation`, `@ApiResponse`, `@Parameter` present in controller class
- **Fix**: Move all annotations to `ExtractionApi` interface; keep controller clean.

### 5.3 Raw JDBC outside infrastructure/jdbc encapsulation
- **Locations**: All extractor implementations (`OracleMetadataExtractor`, `SqlServerMetadataExtractor`, `MariaDbMetadataExtractor`, `PostgreSQLMetadataExtractor`, `AzureSqlMetadataExtractor`) and `JdbcConnectionValidator`.
- **Violation**: Direct use of `DriverManager.getConnection()`, `PreparedStatement`, `ResultSet` outside the mandated `JdbcConnectionFactory` and `JdbcTemplate`.
- **Fix**: Create `JdbcConnectionFactory` and `JdbcTemplate`; refactor extractors to use them.

### 5.4 String comparisons for database engine resolution
- **Location**: `JdbcUrlBuilder.java:40,79` – `SUPPORTED_DB_TYPES.contains(dbType.toLowerCase())`
- **Location**: `DatabaseMetadataExtractorResolver` – uses string‑keyed `Map<String, DatabaseMetadataExtractor>`
- **Violation**: AGENTS.md §4.3 forbids string comparisons for engine resolution; must use `DatabaseType` enum.
- **Fix**: Replace with `DatabaseType` enum lookup.

### 5.5 Authorization header or credentials being logged
- **Location**: `BasicAuthExtractor.java:54` – `log.debug("Extracted Basic Auth credentials for user: {}", username)`
- **Violation**: AGENTS.md §4.6 forbids logging decoded database credentials (username included).
- **Fix**: Remove the log statement; only log that credentials were extracted (without values).

### 5.6 Missing `DatabaseType` enum usage
- **Location**: `DatabaseConnection` (`String dbType`), `ExtractionRequest` (`String dbType`), `ExtractDatabaseStructureService` (string `dbType` parameter).
- **Violation**: AGENTS.md §4.3 requires every database engine to be a `DatabaseType` entry.
- **Fix**: Create `DatabaseType` enum and replace all string references.

### 5.7 Generic `Exception` catching
- **Location**: `ExtractDatabaseStructureService.execute()` catches `Exception`.
- **Violation**: AGENTS.md §3.2 forbids catching generic `Exception` in use case code.
- **Fix**: Define specific domain exceptions and catch only those; let others propagate.

### 5.8 `extendedProperty` handling
- **Current**: Not implemented in connection creation.
- **Requirement**: Must be parsed as `key=value` and passed via `java.util.Properties` to JDBC connection (not appended to URL). Validation pattern `^\S+=.*$`.
- **Fix**: Add validation in DTO; parse in `JdbcConnectionFactory`; never log the value.

## 6. Summary of required actions (by layer)

### Domain layer
1. Create `DatabaseType` and `DdlObjectType` enums.
2. Update `DatabaseConnection` to use `DatabaseType` enum.
3. Move `UnsupportedDatabaseException` to application layer, rename to `UnsupportedDatabaseTypeException`.
4. Delete unused model classes (`Table`, `View`, `Index`, `StoredProcedure`, `Function`, `Sequence`, `DatabaseObject`).

### Port layer
1. Move `DatabaseMetadataExtractor` → `port.out.DdlExtractor` (rename).
2. Move `DdlFileWriter` → `port.out.FileWriter` (rename).
3. Move `DatabaseConnectionValidator` → `port.out.DatabaseConnectionValidator`.
4. Create `port.out.CredentialExtractor` interface.
5. Move `ExtractDatabaseStructureUseCase` → `port.in.ExtractionInputPort` (rename).

### Application layer
1. Rewrite `ExtractDatabaseStructureService` to inject `Map<DatabaseType, DdlExtractor>`, use `DatabaseType` enum, throw specific exceptions.
2. Move `ExtractionRequest` and `ExtractionResult` to `application.usecase` package; update `ExtractionRequest` to use `DatabaseType` enum.

### Infrastructure layer
1. Create `JdbcConnectionFactory` and `JdbcTemplate`.
2. Rewrite all extractor implementations to implement `DdlExtractor`, use `JdbcTemplate`, and relocate to `infrastructure.extractor`.
3. Delete `AzureSqlMetadataExtractor` (merge into `SqlServerDdlExtractor`).
4. Delete `DatabaseMetadataExtractorResolver`.
5. Rewrite `JdbcUrlBuilder` to use `DatabaseType` enum.
6. Rewrite `JdbcConnectionValidator` to use `JdbcConnectionFactory`.
7. Rename `LocalFileSystemWriter` → `LocalFileWriter`.
8. Create `BasicAuthCredentialExtractor` in `infrastructure.security`.

### API/Controller layer
1. Create `ExtractionApi` interface with OpenAPI annotations.
2. Rewrite `DatabaseExtractionController` to implement `ExtractionApi`, use `@RequiredArgsConstructor`, delegate credential extraction and JDBC URL building.
3. Move `GlobalExceptionHandler` to `api` package.

### DTO layer
1. Move all DTOs to `dto` package.
2. Ensure `ExtractionRequestDto` includes `extendedProperty` field with Bean Validation pattern.

### Config layer
1. Split `DatabaseProjectConfig` into `ExtractorRegistryConfig` (with `Map<DatabaseType, DdlExtractor>`) and `SecurityConfig`.
2. Move `SecurityConfig` to `config` package.

---
*Generated for Phase 2 of refactor per docs/refactor.md*