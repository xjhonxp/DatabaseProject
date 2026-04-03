---
title: Database Metadata Extraction Service Specification
version: 1.0
date_created: 2026-04-02
owner: DatabaseProject Team
tags: architecture, design, api, database
---

# Introduction
This specification defines the requirements, design, and interfaces for a Spring Boot REST service that extracts metadata from relational databases and generates DDL scripts organized by schema. The service follows Clean Architecture principles and supports multiple database engines.

## 1. Purpose & Scope
**Purpose**: Provide a standardized, secure, and extensible service for extracting database metadata as DDL files.
**Scope**: 
- Support PostgreSQL, Oracle, SQL Server, Azure SQL Server, MariaDB
- Extract tables, views, indexes, stored procedures, functions, sequences
- Generate files organized by schema and object type
- Expose functionality via REST API 
- User and password for databases is provided by Basic Autentication
- Include filtering capabilities for schemas and object types
- Provide OpenAPI documentation
- OpenAPI documentation Do NOT require authentication
**Audience**: Developers, DevOps engineers, database administrators
**Assumptions**: Users have valid database credentials and network access to target databases.

## 2. Definitions
- **DDL**: Data Definition Language (Only CREATE statements)
- **Metadata**: Structural information about database objects
- **Schema**: Logical container for database objects within a database
- **Basic Authentication**: HTTP authentication method using Base64-encoded username:password
- **Clean Architecture**: Architectural pattern separating concerns into layers (Domain, Application, Infrastructure, Interface Adapters)

## 3. Requirements, Constraints & Guidelines

### Functional Requirements
- **REQ-001**: The service shall accept database connection parameters
- **REQ-002**: The username and password for the database will be provided via via REST API with Basic Authentication credentials in the Authorization header.
- **REQ-003**: The service shall validate database connectivity before starting extraction.
- **REQ-004**: The service shall detect all schemas in the target database dynamically.
- **REQ-005**: The service shall extract metadata for tables, views, indexes, stored procedures, functions, and sequences.
- **REQ-006**: The service shall generate one DDL file per database object.
- **REQ-007**: The service shall organize output files in directory structure: `{projectDir}/{schema}/{object_type}/{object_name}.sql`.
- **REQ-008**: The service shall support filtering by schema name (optional list).
- **REQ-009**: The service shall support filtering by object type (optional list).
- **REQ-010**: The service shall provide OpenAPI documentation via Swagger UI.

### Security Requirements
- **SEC-001**: Credentials must be provided via HTTP Basic Authentication header only, not in request body.
- **SEC-002**: Authorization headers must not be logged.
- **SEC-003**: Credentials must not be exposed in logs, errors, or responses.
- **SEC-004**: All user inputs must be validated.
- **SEC-005**: Errors must not expose internal implementation details.

### Architectural Constraints
- **CON-001**: Follow Clean Architecture principles (Domain, Application, Infrastructure, Interface Adapters).
- **CON-002**: No coupling between controllers and database logic.
- **CON-003**: Use abstraction (strategy pattern) for multi-database support.
- **CON-004**: Code must be testable and maintainable.
- **CON-005**: No hardcoded configurations.
- **CON-006**: Do not create directories that will not be used.

### Guidelines
- **GUD-001**: Use Spring Boot 3.5.3 with Java 21.
- **GUD-002**: Use Maven as build tool.
- **GUD-003**: Package structure: `pe.openstrategy.databaseproject`.
- **GUD-004**: Use Lombok for boilerplate reduction.
- **GUD-005**: Use SLF4J for logging.
- **GUD-006**: Use JDBC DatabaseMetaData for metadata extraction.
- **GUD-007**: Use HikariCP for connection pooling (Spring Boot default).
- **GUD-008**: Use Springdoc OpenAPI for API documentation.

## 4. Interfaces & Data Contracts

### REST API Endpoint
- **Method**: POST
- **Path**: `/api/extract`
- **Authentication**: Basic Auth (Authorization header)
- **Content-Type**: application/json
- **Response**: 202 Accepted (async processing) or 200 OK with summary

### Request Body Schema
```json
{
  "dbType": "postgresql",
  "host": "localhost",
  "port": 5432,
  "database": "testdb",
  "projectDir": "D:/output/",
  "schemas": ["public", "sales"],
  "objectTypes": ["tables", "views", "indexes"]
}
```

### Response Body Schema (Success)
```json
{
  "jobId": "uuid",
  "status": "STARTED",
  "message": "Extraction started successfully",
  "outputPath": "D:/output/"
}
```

### Error Response Schema
```json
{
  "timestamp": "2026-04-02T15:02:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid database type",
  "path": "/api/extract"
}
```

### Database Extraction Port Interface
```java
public interface DatabaseMetadataExtractor {
    List<Schema> extractSchemas(DatabaseConnection connection);
    List<Table> extractTables(Schema schema, DatabaseConnection connection);
    List<View> extractViews(Schema schema, DatabaseConnection connection);
    List<Index> extractIndexes(Schema schema, DatabaseConnection connection);
    List<StoredProcedure> extractStoredProcedures(Schema schema, DatabaseConnection connection);
    List<Function> extractFunctions(Schema schema, DatabaseConnection connection);
    List<Sequence> extractSequences(Schema schema, DatabaseConnection connection);
}
```

## 5. Acceptance Criteria

- **AC-001**: Given valid Basic Auth credentials and connection parameters, when POST /api/extract is called, then the service returns 202 Accepted and begins extraction.
- **AC-002**: Given invalid credentials, when POST /api/extract is called, then the service returns 401 Unauthorized.
- **AC-003**: Given a non-existent database host, when POST /api/extract is called, then the service returns 400 Bad Request with appropriate message.
- **AC-004**: Given schema filter ["public"], when extraction runs, then only objects from "public" schema are generated.
- **AC-005**: Given objectTypes filter ["tables", "views"], when extraction runs, then only table and view DDL files are created.
- **AC-006**: After successful extraction, the file structure `{projectDir}/{schema}/{object_type}/{object_name}.sql` exists with valid DDL content.
- **AC-007**: Swagger UI is accessible at `/swagger-ui.html` with complete endpoint documentation.

## 6. Test Automation Strategy

- **Test Levels**: Unit, Integration, End-to-End
- **Frameworks**: JUnit 5, Mockito, Testcontainers, Spring Boot Test
- **Test Data Management**: Use embedded H2 database for unit tests; Testcontainers with PostgreSQL for integration tests
- **CI/CD Integration**: Automated testing in GitHub Actions (if configured)
- **Coverage Requirements**: Minimum 80% line coverage for core business logic
- **Performance Testing**: Not required for initial version; monitor extraction time for large databases

## 7. Rationale & Context
- **Clean Architecture** ensures separation of concerns and testability.
- **Strategy pattern** for database engines allows easy addition of new databases.
- **Basic Authentication** is simple and widely supported; credentials kept out of request body for security.
- **Filtering** reduces unnecessary extraction and output.
- **OpenAPI documentation** improves API discoverability and consumption.

## 8. Dependencies & External Integrations

### External Systems
- **EXT-001**: Target Database - Relational database system (PostgreSQL, Oracle, etc.) for metadata extraction.

### Third-Party Services
- **SVC-001**: None required.

### Infrastructure Dependencies
- **INF-001**: File System - Write access to `projectDir` for DDL file generation.

### Data Dependencies
- **DAT-001**: Database Metadata - Accessed via JDBC DatabaseMetaData.

### Technology Platform Dependencies
- **PLT-001**: Java 21 Runtime - Required for execution.
- **PLT-002**: Spring Boot 3.5.3 - Framework for REST service.
- **PLT-003**: Database JDBC Drivers - PostgreSQL, Oracle, SQL Server, MariaDB drivers.

### Compliance Dependencies
- **COM-001**: No specific compliance requirements.

## 9. Examples & Edge Cases

```java
// Example of extracting tables from PostgreSQL
DatabaseConnection conn = new DatabaseConnection("jdbc:postgresql://localhost:5432/testdb", "user", "pass");
PostgreSQLMetadataExtractor extractor = new PostgreSQLMetadataExtractor();
List<Table> tables = extractor.extractTables(new Schema("public"), conn);
// Generates: CREATE TABLE public.users (id SERIAL PRIMARY KEY, name VARCHAR(100));
```

**Edge Cases**:
- Database with no schemas (single schema databases)
- Empty schema (no objects)
- Very long object names (file system limits)
- Special characters in object names
- Insufficient file system permissions
- Network timeout during extraction

## 10. Validation Criteria
- All acceptance criteria (AC-001 through AC-007) pass.
- Code compiles without warnings.
- All tests pass (unit, integration).
- No security vulnerabilities detected (credential logging, etc.).
- OpenAPI documentation matches actual API behavior.

## 11. Related Specifications / Further Reading
- [Project Context](../context/project.md)
- [Implementation Plan](../database-project-plan.md)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Clean Architecture by Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)