# Refactor Phase 1 — Current State Mapping

**Root package:** `pe.openstrategy.databaseproject`

**Target structure per AGENTS.md:**

- `api` – OpenAPI-annotated interfaces only
- `controller` – Implements api interfaces, no logic
- `application` – Use cases (orchestrates ports)
- `domain` – Enums and value objects only
- `port` – Interfaces used by application layer (`in`, `out`)
- `infrastructure` – Implements port interfaces (jdbc, extractor, filesystem, security)
- `dto` – Request/response objects, no logic
- `config` – Spring configuration beans

## Mapping Table

| Current FQCN | Responsibility | Target Package | Action |
|--------------|----------------|----------------|--------|
| **DOMAIN** | | | |
| `pe.openstrategy.databaseproject.domain.valueobject.DatabaseConnection` | Represents database connection configuration | `domain.valueobject` | REWRITE (use DatabaseType enum), MOVE |
| `pe.openstrategy.databaseproject.domain.valueobject.DdlStatement` | Represents a DDL statement | `domain.valueobject` | MOVE |
| `pe.openstrategy.databaseproject.domain.valueobject.Schema` | Represents a database schema name | `domain.valueobject` | MOVE |
| `pe.openstrategy.databaseproject.domain.model.DatabaseObject` | Base class for all database metadata objects | `domain.model` | DELETE (unused) |
| `pe.openstrategy.databaseproject.domain.model.Table` | Represents a database table | `domain.model` | DELETE (unused) |
| `pe.openstrategy.databaseproject.domain.model.View` | Represents a database view | `domain.model` | DELETE (unused) |
| `pe.openstrategy.databaseproject.domain.model.Index` | Represents a database index | `domain.model` | DELETE (unused) |
| `pe.openstrategy.databaseproject.domain.model.StoredProcedure` | Represents a stored procedure | `domain.model` | DELETE (unused) |
| `pe.openstrategy.databaseproject.domain.model.Function` | Represents a database function | `domain.model` | DELETE (unused) |
| `pe.openstrategy.databaseproject.domain.model.Sequence` | Represents a database sequence | `domain.model` | DELETE (unused) |
| `pe.openstrategy.databaseproject.domain.exception.UnsupportedDatabaseException` | Thrown when unsupported database type is requested | `application.exception` | MOVE, RENAME (UnsupportedDatabaseTypeException) |
| **APPLICATION** | | | |
| `pe.openstrategy.databaseproject.application.usercase.ExtractDatabaseStructureUseCase` | Defines extraction contract | `port.in.ExtractionInputPort` | RENAME, MOVE |
| `pe.openstrategy.databaseproject.application.usercase.ExtractDatabaseStructureService` | Implements extraction use case | `application.usecase.ExtractDatabaseStructureUseCase` | REWRITE (inject Map<DatabaseType, DdlExtractor>, remove infrastructure deps, use DatabaseType enum) |
| `pe.openstrategy.databaseproject.application.dto.ExtractionRequest` | Input data for extraction use case | `application.usecase.ExtractionRequest` | MOVE, REWRITE (use DatabaseType enum) |
| `pe.openstrategy.databaseproject.application.dto.ExtractionResult` | Output data from extraction use case | `application.usecase.ExtractionResult` | MOVE |
| `pe.openstrategy.databaseproject.application.port.DatabaseMetadataExtractor` | Port for extracting metadata | `port.out.DdlExtractor` | RENAME, MOVE |
| `pe.openstrategy.databaseproject.application.port.DatabaseConnectionValidator` | Port for validating database connectivity | `port.out.DatabaseConnectionValidator` | MOVE |
| `pe.openstrategy.databaseproject.application.port.DdlFileWriter` | Port for writing DDL statements to files | `port.out.FileWriter` | RENAME, MOVE |
| **INFRASTRUCTURE** | | | |
| `pe.openstrategy.databaseproject.infrastructure.database.oracle.OracleMetadataExtractor` | Oracle metadata extractor | `infrastructure.extractor.OracleDdlExtractor` | REWRITE (implement DdlExtractor, use JdbcTemplate), MOVE |
| `pe.openstrategy.databaseproject.infrastructure.database.sqlserver.SqlServerMetadataExtractor` | SQL Server metadata extractor | `infrastructure.extractor.SqlServerDdlExtractor` | REWRITE (implement DdlExtractor, use JdbcTemplate), MOVE |
| `pe.openstrategy.databaseproject.infrastructure.database.mariadb.MariaDbMetadataExtractor` | MariaDB metadata extractor | `infrastructure.extractor.MariaDbDdlExtractor` | REWRITE (implement DdlExtractor, use JdbcTemplate), MOVE |
| `pe.openstrategy.databaseproject.infrastructure.database.postgresql.PostgreSQLMetadataExtractor` | PostgreSQL metadata extractor | `infrastructure.extractor.PostgresDdlExtractor` | REWRITE (implement DdlExtractor, use JdbcTemplate), MOVE |
| `pe.openstrategy.databaseproject.infrastructure.database.azuresql.AzureSqlMetadataExtractor` | Azure SQL metadata extractor | `infrastructure.extractor.SqlServerDdlExtractor` | DELETE (merge into SqlServerDdlExtractor) |
| `pe.openstrategy.databaseproject.infrastructure.database.DatabaseMetadataExtractorResolver` | Resolves extractor by database type | `config.ExtractorRegistryConfig` | DELETE (replace with Map<DatabaseType, DdlExtractor> bean) |
| `pe.openstrategy.databaseproject.infrastructure.database.jdbc.JdbcUrlBuilder` | Builds JDBC URLs | `infrastructure.jdbc.JdbcUrlBuilder` | REWRITE (use DatabaseType enum) |
| `pe.openstrategy.databaseproject.infrastructure.database.jdbc.JdbcConnectionValidator` | Validates database connection | `infrastructure.jdbc.JdbcConnectionValidator` | REWRITE (use JdbcConnectionFactory) |
| `pe.openstrategy.databaseproject.infrastructure.filesystem.LocalFileSystemWriter` | Writes DDL to local filesystem | `infrastructure.filesystem.LocalFileWriter` | RENAME, MOVE |
| `pe.openstrategy.databaseproject.infrastructure.config.DatabaseProjectConfig` | Main Spring configuration | `config.ExtractorRegistryConfig` + `config.SecurityConfig` | REWRITE (split into two config classes) |
| `pe.openstrategy.databaseproject.infrastructure.config.SecurityConfig` | Security configuration | `config.SecurityConfig` | MOVE |
| **INTERFACE ADAPTERS (WEB)** | | | |
| `pe.openstrategy.databaseproject.interfaceadapters.web.controller.DatabaseExtractionController` | REST controller for extraction | `controller.ExtractionController` | REWRITE (implement ExtractionApi, remove @Autowired, delegate credential extraction, remove JDBC URL building) |
| `pe.openstrategy.databaseproject.interfaceadapters.web.dto.ExtractionRequestDto` | HTTP request DTO | `dto.ExtractionRequestDto` | MOVE |
| `pe.openstrategy.databaseproject.interfaceadapters.web.dto.ExtractionResponseDto` | HTTP response DTO | `dto.ExtractionResponseDto` | MOVE |
| `pe.openstrategy.databaseproject.interfaceadapters.web.dto.ErrorResponseDto` | Error response DTO | `dto.ErrorResponseDto` | MOVE |
| `pe.openstrategy.databaseproject.interfaceadapters.web.exception.GlobalExceptionHandler` | Global exception handler | `api.GlobalExceptionHandler` | MOVE |
| `pe.openstrategy.databaseproject.interfaceadapters.web.security.BasicAuthExtractor` | Extracts Basic Auth credentials | `infrastructure.security.BasicAuthCredentialExtractor` | REWRITE (implement CredentialExtractor, remove credential logging) |
| **OTHER** | | | |
| `pe.openstrategy.databaseproject.DatabaseProjectApplication` | Spring Boot main class | `DatabaseProjectApplication` | KEEP (but may need package change) |
| **MISSING CLASSES** | | | |
| `DatabaseType` enum | Database engine enumeration | `domain.DatabaseType` | CREATE |
| `DdlObjectType` enum | DDL object type enumeration | `domain.DdlObjectType` | CREATE |
| `ExtractionApi` interface | OpenAPI-annotated API interface | `api.ExtractionApi` | CREATE |
| `JdbcConnectionFactory` | Sole class creating JDBC connections | `infrastructure.jdbc.JdbcConnectionFactory` | CREATE |
| `JdbcTemplate` | Sole class referencing JDBC objects | `infrastructure.jdbc.JdbcTemplate` | CREATE |
| `CredentialExtractor` port | Extracts credentials from request | `port.out.CredentialExtractor` | CREATE |
| `ExtractorRegistryConfig` | Configures Map<DatabaseType, DdlExtractor> | `config.ExtractorRegistryConfig` | CREATE |

## Notes

- **DatabaseType enum** must include POSTGRESQL, ORACLE, SQL_SERVER, AZURE_SQL, MARIADB with driverClass, defaultPort, dialect fields.
- **DdlObjectType enum** must include TABLE, VIEW, INDEX, PROCEDURE, FUNCTION, SEQUENCE.
- **All string comparisons for database engine resolution** must be replaced with `DatabaseType` enum lookups.
- **No @Autowired annotations** allowed; use `@RequiredArgsConstructor`.
- **OpenAPI annotations** only in `ExtractionApi` interface and DTOs.
- **JDBC encapsulation**: `JdbcConnectionFactory` and `JdbcTemplate` must centralize all JDBC operations.
- **Security**: Never log Authorization header, credentials, or `extendedProperty` value.
- **ExtendedProperty**: Must be parsed as `key=value` and passed via `java.util.Properties` to JDBC connection.

---
*Generated for Phase 1 of refactor per docs/refactor.md*