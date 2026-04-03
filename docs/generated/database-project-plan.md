# Plan: DatabaseProject Implementation

**Generated**: 2026-04-02
**Estimated Complexity**: High

## Overview
Build a Spring Boot REST service that extracts metadata from multiple relational database engines and generates DDL creation scripts organized by schema. The service follows Clean Architecture principles, supports PostgreSQL, Oracle, SQL Server, Azure SQL Server, and MariaDB, and uses HTTP Basic Authentication for credentials.

## Prerequisites
- Java 21
- Maven 3.8+
- Spring Boot 3.5.3
- Access to sample databases for testing

## Sprint 1: Project Setup and Foundation
**Goal**: Create Maven project with Clean Architecture structure and basic dependencies.
**Demo/Validation**:
- Project compiles with `mvn clean compile`
- Spring Boot application starts without errors

### Task 1.1: Initialize Maven Project
- **Location**: `pom.xml`
- **Description**: Create Maven project with Spring Boot 3.5.3, Java 21, and required dependencies (Spring Boot Starter Web, Spring Security, Springdoc OpenAPI, database drivers, Lombok, etc.)
- **Dependencies**: None
- **Acceptance Criteria**:
  - `pom.xml` includes all necessary dependencies
  - Project compiles successfully
- **Validation**: Run `mvn clean compile`

### Task 1.2: Create Package Structure
- **Location**: `src/main/java/pe/openstrategy/databaseproject/`
- **Description**: Create Clean Architecture layer packages: `domain`, `application`, `infrastructure`, `interfaceadapters`
- **Dependencies**: Task 1.1
- **Acceptance Criteria**:
  - All layer packages exist
  - No code yet, just directories
- **Validation**: Check directory structure

### Task 1.3: Configure Application Properties
- **Location**: `src/main/resources/application.yml`
- **Description**: Create configuration file with server port, logging, and default settings
- **Dependencies**: Task 1.1
- **Acceptance Criteria**:
  - Application starts on port 8080
  - Basic logging configured
- **Validation**: Run `mvn spring-boot:run` and verify startup

## Sprint 2: Authentication and Request Handling
**Goal**: Implement Basic Auth extraction, request DTOs, validation, and error handling.
**Demo/Validation**:
- REST endpoint accepts Basic Auth header
- Request validation works
- Proper error responses returned

### Task 2.1: Implement Basic Auth Credential Extractor
- **Location**: `src/main/java/pe/openstrategy/databaseproject/interfaceadapters/security/`
- **Description**: Create component to extract and decode credentials from Authorization header securely
- **Dependencies**: Task 1.2
- **Acceptance Criteria**:
  - Credentials extracted without logging
  - Secure handling of decoded values
- **Validation**: Unit tests for extraction logic

### Task 2.2: Create Request/Response DTOs
- **Location**: `src/main/java/pe/openstrategy/databaseproject/interfaceadapters/web/dto/`
- **Description**: Define DTOs for extraction request and response
- **Dependencies**: Task 1.2
- **Acceptance Criteria**:
  - DTOs include all required fields with validation annotations
  - Schema and object type filtering fields included
- **Validation**: Compilation and simple serialization test

### Task 2.3: Implement Global Exception Handler
- **Location**: `src/main/java/pe/openstrategy/databaseproject/interfaceadapters/web/exception/`
- **Description**: Create `@ControllerAdvice` to handle validation errors, authentication failures, and internal errors
- **Dependencies**: Task 2.2
- **Acceptance Criteria**:
  - Consistent error response format
  - No sensitive information leaked
- **Validation**: Test with invalid requests

## Sprint 3: Database Abstraction and Domain Models
**Goal**: Define Clean Architecture interfaces and domain models for database metadata extraction.
**Demo/Validation**:
- Domain models represent database objects
- Port interfaces defined for extraction strategies

### Task 3.1: Create Domain Entities
- **Location**: `src/main/java/pe/openstrategy/databaseproject/domain/`
- **Description**: Define `DatabaseObject`, `Table`, `View`, `Index`, `StoredProcedure`, `Function`, `Sequence` entities
- **Dependencies**: Task 1.2
- **Acceptance Criteria**:
  - Entities include essential metadata fields
  - Immutable where appropriate
- **Validation**: Compilation and simple instantiation tests

### Task 3.2: Define Use Case Interface
- **Location**: `src/main/java/pe/openstrategy/databaseproject/application/usecase/`
- **Description**: Create `ExtractDatabaseStructureUseCase` interface with input/output ports
- **Dependencies**: Task 3.1
- **Acceptance Criteria**:
  - Interface follows Clean Architecture (input/output ports)
  - Supports filtering parameters
- **Validation**: Compilation

### Task 3.3: Define Database Extraction Port
- **Location**: `src/main/java/pe/openstrategy/databaseproject/application/port/`
- **Description**: Create `DatabaseMetadataExtractor` port interface with engine-specific implementations
- **Dependencies**: Task 3.1
- **Acceptance Criteria**:
  - Interface supports multiple database engines
  - Methods for each object type extraction
- **Validation**: Compilation

## Sprint 4: Multi-Database Engine Implementation

**Goal**: Implement metadata extraction for all supported database engines using a pluggable strategy pattern.

**Supported Engines**:
- PostgreSQL
- Oracle
- SQL Server
- Azure SQL Server
- DB2 for i
- MariaDB

---

### Task 4.1: Define Extraction Strategy Resolver

- Create a factory or resolver:
  DatabaseMetadataExtractorResolver

- Input: dbType
- Output: correct extractor implementation

---

### Task 4.2: Implement Extractors

Implement:

- PostgreSQLMetadataExtractor
- OracleMetadataExtractor
- SqlServerMetadataExtractor
- AzureSqlMetadataExtractor
- DB2iMetadataExtractor
- MariaDbMetadataExtractor

Each must:
- Implement DatabaseMetadataExtractor
- Use JDBC DatabaseMetaData when possible
- Handle engine-specific differences

---

### Task 4.3: Handle Engine Differences

- Naming conventions (schemas vs libraries en DB2)
- Procedures vs packages (Oracle)
- Sequences vs identity columns
- Case sensitivity

---

### Task 4.4: Integration Tests per Engine

- Use Testcontainers where possible
- Validate extraction per engine

---

### Acceptance Criteria

- All engines selectable via dbType
- Correct extractor resolved dynamically
- No conditional logic in controller/use case
- Strategy pattern enforced

## Sprint 5: File System Output
**Goal**: Write DDL files to organized directory structure.
**Demo/Validation**:
- Files created in correct schema/type directories
- DDL content written correctly

### Task 5.1: Implement File Writer Port
- **Location**: `src/main/java/pe/openstrategy/databaseproject/application/port/`
- **Description**: Create `DdlFileWriter` interface for writing files
- **Dependencies**: Task 3.1
- **Acceptance Criteria**:
  - Interface supports writing files by schema and object type
- **Validation**: Compilation

### Task 5.2: Implement Local File System Writer
- **Location**: `src/main/java/pe/openstrategy/databaseproject/infrastructure/filesystem/`
- **Description**: Create `LocalFileSystemWriter` that writes to `projectDir`
- **Dependencies**: Task 5.1
- **Acceptance Criteria**:
  - Creates directory structure: `schema/{tables,views,indexes,procedures,functions,sequences}/`
  - One file per object
- **Validation**: Files created with correct content

## Sprint 6: Use Case Implementation and REST Controller
**Goal**: Wire everything together in the use case and expose via REST endpoint.
**Demo/Validation**:
- REST endpoint accepts request and triggers extraction
- Files generated in specified directory

### Task 6.1: Implement ExtractDatabaseStructureUseCase
- **Location**: `src/main/java/pe/openstrategy/databaseproject/application/usecase/`
- **Description**: Create use case implementation coordinating extractor and file writer
- **Dependencies**: Task 3.2, Task 4.1, Task 5.2
- **Acceptance Criteria**:
  - Validates connection first
  - Applies filtering if provided
  - Handles errors gracefully
- **Validation**: Unit tests with mocks

### Task 6.2: Create REST Controller
- **Location**: `src/main/java/pe/openstrategy/databaseproject/interfaceadapters/web/controller/`
- **Description**: Create `DatabaseExtractionController` with POST endpoint
- **Dependencies**: Task 2.1, Task 2.2, Task 6.1
- **Acceptance Criteria**:
  - Endpoint at `/api/extract`
  - Uses Basic Auth
  - Returns appropriate HTTP status
- **Validation**: Integration test with MockMvc

### Task 6.3: Add Swagger Documentation
- **Location**: `src/main/java/pe/openstrategy/databaseproject/interfaceadapters/web/config/`
- **Description**: Configure Springdoc OpenAPI for API documentation
- **Dependencies**: Task 6.2
- **Acceptance Criteria**:
  - Swagger UI available at `/swagger-ui.html`
  - Endpoint documented with examples
- **Validation**: Access Swagger UI in browser

## Sprint 7: Testing and Edge Cases
**Goal**: Comprehensive test coverage and handling of edge cases.
**Demo/Validation**:
- All tests pass
- Edge cases handled appropriately

### Task 7.1: Write Unit Tests
- **Location**: `src/test/java/pe/openstrategy/databaseproject/`
- **Description**: Unit tests for use case, services, utilities
- **Dependencies**: Task 6.1
- **Acceptance Criteria**:
  - Test coverage >80% for core logic
  - Mock external dependencies
- **Validation**: `mvn test` passes

### Task 7.2: Write Integration Tests
- **Location**: `src/test/java/pe/openstrategy/databaseproject/`
- **Description**: Integration tests for controller with embedded database
- **Dependencies**: Task 6.2
- **Acceptance Criteria**:
  - Tests verify full flow with test database
  - Clean up test files
- **Validation**: Integration test suite passes

### Task 7.3: Edge Case Scenarios
- **Location**: `src/test/java/pe/openstrategy/databaseproject/`
- **Description**: Test invalid credentials, connection failures, empty schemas, permission issues
- **Dependencies**: Task 7.1
- **Acceptance Criteria**:
  - Graceful error handling for all edge cases
- **Validation**: Edge case tests pass

## Testing Strategy
- Unit tests for domain logic and use cases
- Integration tests for REST endpoints with test containers
- Manual testing with real databases (PostgreSQL, etc.)
- Validation of generated DDL against original schema

## Potential Risks & Gotchas
- **Database driver compatibility**: Ensure JDBC drivers work with all supported versions
- **Large metadata extraction**: May cause memory issues; implement streaming or pagination
- **File system permissions**: Handle write permission errors gracefully
- **Network timeouts**: Set appropriate connection and query timeouts
- **Credential security**: Never log or expose credentials in errors

## Rollback Plan
- Each sprint produces committable code; revert to previous commit if issues arise
- Database changes are read-only; no risk of data loss
- File system writes can be cleaned up by deleting the output directory