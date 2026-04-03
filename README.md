# DatabaseProject

Universal relational database metadata extractor built with Spring Boot and Clean Architecture.

## Overview

This project provides a REST service that connects to relational databases, extracts metadata objects (tables, views, indexes, stored procedures, functions, sequences), and generates DDL scripts organized by schema.

## Features

- Supports PostgreSQL, Oracle, SQL Server, Azure SQL Server, MariaDB
- HTTP Basic Authentication for secure credential handling
- Schema and object type filtering
- Clean Architecture for maintainability and testability
- OpenAPI documentation via Swagger UI
- Configurable output directory structure

## Architecture

The project follows Clean Architecture with four layers:

1. **Domain**: Core business entities and value objects
2. **Application**: Use cases and port interfaces
3. **Infrastructure**: Database-specific implementations and file system writer
4. **Interface Adapters**: REST controllers, DTOs, security, and exception handlers

## Getting Started

### Prerequisites

- Java 21
- Maven 3.8+

### Building

```bash
mvn clean compile
```

### Running

```bash
mvn spring-boot:run
```

The application will start on port 8080. Swagger UI will be available at `http://localhost:8081/swagger-ui.html`.

### API Usage

Send a POST request to `/api/extract` with Basic Authentication header and JSON body:

```json
{
  "dbType": "postgresql",
  "host": "localhost",
  "port": 5432,
  "database": "testdb",
  "projectDir": "/tmp/output",
  "schemas": ["public"],
  "objectTypes": ["tables", "views"]
}
```

## Project Structure

```
src/main/java/pe/openstrategy/databaseproject/
├── domain/                    # Core business models
├── application/               # Use cases and ports
├── infrastructure/           # Database and file system implementations
└── interfaceadapters/        # Web controllers and DTOs
```

## Development

### Adding a New Database Engine

1. Implement `DatabaseMetadataExtractor` interface in `infrastructure/database/<engine>/`
2. Add JDBC driver dependency to `pom.xml`
3. Register the implementation via configuration

### Testing

Run unit tests:

```bash
mvn test
```

Integration tests require Testcontainers and will spin up actual database instances.

## Configuration

See `src/main/resources/application.yml` for configuration options.

## Security

- Credentials are transmitted via HTTP Basic Authentication (Base64 encoded)
- Authorization headers are never logged
- Input validation prevents injection attacks
- Error messages do not expose internal details

## License

Internal use only.