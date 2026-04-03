# DatabaseProject Architecture

## Clean Architecture Layers

### Domain Layer
- **Entities**: Core business objects (`DatabaseObject`, `Table`, `View`, `Index`, `StoredProcedure`, `Function`, `Sequence`)
- **Value Objects**: `DatabaseConnection`, `Schema`, `DdlStatement`
- **Domain Services**: (None initially)

### Application Layer
- **Use Cases**: `ExtractDatabaseStructureUseCase`
- **Ports (Interfaces)**:
  - `DatabaseMetadataExtractor` - for extracting metadata from different database engines
  - `DdlFileWriter` - for writing DDL files to filesystem
  - `DatabaseConnectionValidator` - for validating database connectivity
- **Input/Output Models**: `ExtractionRequest`, `ExtractionResult`

### Infrastructure Layer
- **Database Metadata Extractors**: `PostgreSQLMetadataExtractor`, `OracleMetadataExtractor`, `SqlServerMetadataExtractor`, `MariaDBMetadataExtractor`
- **File System Writer**: `LocalFileSystemWriter`
- **Connection Validator**: `JdbcConnectionValidator`
- **Database Drivers**: Managed via Maven dependencies

### Interface Adapters Layer
- **Controllers**: `DatabaseExtractionController` (REST)
- **DTOs**: `ExtractionRequestDto`, `ExtractionResponseDto`, `ErrorResponseDto`
- **Security**: `BasicAuthExtractor` (extracts credentials from Authorization header)
- **Exception Handlers**: `GlobalExceptionHandler` (`@ControllerAdvice`)
- **OpenAPI Configuration**: `OpenApiConfig`

## Package Structure

```
src/main/java/pe/openstrategy/databaseproject/
├── domain/
│   ├── model/
│   │   ├── DatabaseObject.java
│   │   ├── Table.java
│   │   ├── View.java
│   │   ├── Index.java
│   │   ├── StoredProcedure.java
│   │   ├── Function.java
│   │   └── Sequence.java
│   ├── valueobject/
│   │   ├── DatabaseConnection.java
│   │   ├── Schema.java
│   │   └── DdlStatement.java
│   └── exception/
│       └── DomainException.java
├── application/
│   ├── usercase/
│   │   └── ExtractDatabaseStructureUseCase.java
│   ├── port/
│   │   ├── DatabaseMetadataExtractor.java
│   │   ├── DdlFileWriter.java
│   │   └── DatabaseConnectionValidator.java
│   ├── dto/
│   │   ├── ExtractionRequest.java
│   │   └── ExtractionResult.java
│   └── exception/
│       └── ApplicationException.java
├── infrastructure/
│   ├── database/
│   │   ├── jdbc/
│   │   │   └── JdbcConnectionValidator.java
│   │   └── (engine)/
│   │       ├── postgresql/
│   │       │   └── PostgreSQLMetadataExtractor.java
│   │       ├── oracle/
│   │       │   └── OracleMetadataExtractor.java
│   │       ├── sqlserver/
│   │       │   └── SqlServerMetadataExtractor.java
│   │       └── mariadb/
│   │           └── MariaDBMetadataExtractor.java
│   ├── filesystem/
│   │   └── LocalFileSystemWriter.java
│   └── config/
│       └── DatabaseDriverConfig.java
└── interfaceadapters/
    ├── web/
    │   ├── controller/
    │   │   └── DatabaseExtractionController.java
    │   ├── dto/
    │   │   ├── ExtractionRequestDto.java
    │   │   ├── ExtractionResponseDto.java
    │   │   └── ErrorResponseDto.java
    │   ├── security/
    │   │   └── BasicAuthExtractor.java
    │   └── exception/
    │       └── GlobalExceptionHandler.java
    ├── openapi/
    │   └── OpenApiConfig.java
    └── config/
        └── WebConfig.java
```

## Dependency Flow

- **Interface Adapters** depend on **Application** layer (use cases)
- **Application** layer depends on **Domain** layer (entities) and defines ports
- **Infrastructure** layer implements ports defined in **Application** layer
- **Domain** layer has no dependencies on other layers

## Database Engine Strategy Pattern

```
DatabaseMetadataExtractor (interface)
        ↑
        | implements
        |
+-----------------------+
| PostgreSQLMetadataExtractor |
| OracleMetadataExtractor    |
| SqlServerMetadataExtractor |
| MariaDBMetadataExtractor   |
+-----------------------+
```

The appropriate extractor is selected based on `dbType` parameter.

## Configuration Management

- **Spring Boot**: `application.yml` for server, logging, default settings
- **Database Drivers**: Dynamically loaded based on `dbType`; all drivers included as dependencies
- **Connection Pooling**: HikariCP (Spring Boot default)

## Security Design

- **Basic Authentication**: Credentials extracted from `Authorization` header
- **No Storage**: Credentials used only for JDBC connection, not persisted
- **Secure Logging**: Authorization header filtered from logs via `logback.xml`
- **Input Validation**: All request fields validated with Bean Validation 3.0

## Error Handling Hierarchy

```
DatabaseProjectException (abstract)
├── DomainException
├── ApplicationException
├── InfrastructureException
└── WebException (mapped to HTTP status codes)
```

## Testing Strategy

- **Domain Layer**: Unit tests with JUnit 5
- **Application Layer**: Unit tests with mocked ports
- **Infrastructure Layer**: Integration tests with Testcontainers
- **Interface Adapters**: Spring Boot Test with MockMvc

## Technology Stack

- **Java**: 21
- **Spring Boot**: 3.5.3
- **Build Tool**: Maven
- **Database Drivers**: PostgreSQL (42.7.x), Oracle (ojdbc11), SQL Server (mssql-jdbc), MariaDB (mariadb-java-client)
- **Documentation**: Springdoc OpenAPI 2.x
- **Testing**: JUnit 5, Mockito, Testcontainers
- **Logging**: SLF4J with Logback