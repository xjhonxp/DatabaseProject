# Project: databaseProject

## Description

Universal metadata extractor for relational databases.

This project provides a Spring Boot REST service that receives database connection
parameters via a REST endpoint, connects to the target database, extracts metadata
(tables, views, indexes, stored procedures, functions, sequences), and generates
DDL scripts organized in a folder structure by schema on the server's filesystem.

---

## Technology stack

- Java 21
- Spring Boot 3.3.x
- Clean Architecture (domain / application / infrastructure / api layers)
- JDBC (dynamic driver loading per database engine — no Spring Data JPA)
- Maven
- JUnit 5 + Mockito (unit tests)
- Testcontainers (integration tests)

---

## Supported database engines

All engines below must be implemented in the first and only delivery.
No incremental approach is acceptable.

| Engine       | JDBC Driver class                                        | Default port |
|--------------|----------------------------------------------------------|--------------|
| PostgreSQL   | org.postgresql.Driver                                    | 5432         |
| Oracle       | oracle.jdbc.OracleDriver                                 | 1521         |
| SQL Server   | com.microsoft.sqlserver.jdbc.SQLServerDriver             | 1433         |
| Azure SQL    | com.microsoft.sqlserver.jdbc.SQLServerDriver             | 1433         |
| MariaDB      | org.mariadb.jdbc.Driver                                  | 3306         |

> NOTE: Azure SQL and SQL Server share the same JDBC driver and the same
> DdlExtractor implementation. The DatabaseType enum must contain both entries
> but both must resolve to the same extractor class. Do not create a duplicate
> implementation.

All engine identifiers must be represented as a `DatabaseType` enum.
No string comparisons (equals, contains, switch on String) are allowed for
engine resolution anywhere in the codebase.

---

## REST endpoint

### Request

```
POST /api/v1/extract
Content-Type: application/json
Authorization: Basic <base64(dbUsername:dbPassword)>
```

Body:
```json
{
  "dbType": "oracle",
  "host": "localhost",
  "port": 1521,
  "database": "ORCL",
  "projectDir": "D:/output/",
  "extendedProperty": "oracle.jdbc.proxyClientName=myProxyUser"
}
```

> `extendedProperty` is optional. When omitted, the field must be absent or null.
> Example without extended property:
> ```json
> {
>   "dbType": "postgresql",
>   "host": "localhost",
>   "port": 5432,
>   "database": "testdb",
>   "projectDir": "D:/output/"
> }
> ```

### Field definitions

| Field              | Type    | Required | Validation                                              |
|--------------------|---------|----------|---------------------------------------------------------|
| dbType             | String  | Yes      | Must match a valid DatabaseType enum value              |
| host               | String  | Yes      | Non-blank, max 255 chars                                |
| port               | Integer | Yes      | Between 1 and 65535                                     |
| database           | String  | Yes      | Non-blank, max 255 chars                                |
| projectDir         | String  | Yes      | Non-blank; must be a valid absolute path on the server  |
| extendedProperty   | String  | No       | When present: must match the format `key=value` exactly |

**`extendedProperty` format rules (validated when the field is present):**
- Format: `key=value` — exactly one `=` separator, no leading or trailing whitespace.
- `key` must be non-blank.
- `value` may be blank (e.g., `"someFlag="` is valid).
- The entire string must not exceed 512 characters.
- Validation must reject strings with no `=`, multiple `=` at position 0 (empty key),
  or values exceeding the length limit.
- Examples of valid values: `oracle.jdbc.proxyClientName=myUser`, `loginTimeout=30`
- Examples of invalid values: `noEqualsSign`, `=missingKey`, `   =spaceKey`

### Authentication

Database credentials (username and password) are provided exclusively via
HTTP Basic Authentication in the Authorization header.
- The service must decode the Base64 credentials from the header.
- Credentials must NOT be included in the request body.
- The Authorization header and decoded credentials must NEVER appear in logs.

### Response — success (200 OK)

```json
{
  "status": "SUCCESS",
  "schemasProcessed": 3,
  "objectsExtracted": 142,
  "outputDirectory": "D:/output/",
  "schemas": [
    {
      "name": "public",
      "tables": 40,
      "views": 5,
      "indexes": 20,
      "procedures": 3,
      "functions": 2,
      "sequences": 1
    }
  ]
}
```

### Response — validation error (400 Bad Request)

```json
{
  "status": "VALIDATION_ERROR",
  "errors": [
    { "field": "dbType", "message": "must be a supported database engine" },
    { "field": "extendedProperty", "message": "must follow the format key=value" }
  ]
}
```

### Response — connection failure (502 Bad Gateway)

```json
{
  "status": "CONNECTION_ERROR",
  "message": "Unable to connect to the target database."
}
```

> IMPORTANT: Internal exception messages, stack traces, driver errors, and
> credentials must never appear in any response body.

---

## File system output structure

```
{projectDir}/
  {schema_name}/
    tables/
      {table_name}.sql
    views/
      {view_name}.sql
    indexes/
      {index_name}.sql
    procedures/
      {procedure_name}.sql
    functions/
      {function_name}.sql
    sequences/
      {sequence_name}.sql
```

- One file per database object.
- Each file contains the complete DDL definition for that object.
- Folder names are lowercase.
- If a schema has no objects of a given type, that subfolder is not created.
- `projectDir` is a server-side path. The service writes files to the server's
  local filesystem. It is the caller's responsibility to provide a valid,
  writable absolute path.

---

## Functional requirements

- Validate the HTTP request (Bean Validation on DTO) before any database call.
- Validate database connectivity before beginning extraction.
- Decode Basic Auth credentials securely from the Authorization header.
- When `extendedProperty` is present, parse it into a `key` and `value` by splitting
  on the first `=` only. Inject the parsed key-value pair into the `java.util.Properties`
  object used to create the JDBC connection. Do not append it to the JDBC URL.
- Detect all schemas in the target database dynamically (do not hardcode schema names).
- Extract all supported object types for each detected schema.
- Generate one .sql file per object using the file structure above.
- Resolve the correct DdlExtractor implementation at runtime using the
  `DatabaseType` enum and a strategy registry (e.g., a Map<DatabaseType, DdlExtractor>
  registered as a Spring bean).
- All five database engines must be supported in the same delivery.

---

## Non-functional requirements

- Clean Architecture: domain classes must have zero Spring dependencies.
  Dependencies flow inward: api → application → domain ← infrastructure.
- High cohesion and low coupling between layers.
- No hardcoded configuration values (use application.properties or constants).
- Code must be unit-testable without a running database (use interfaces/ports).
- JDBC connections must be created in a single centralized factory class.
- No raw Connection, PreparedStatement, or ResultSet references outside the
  JDBC infrastructure layer.

---

## Security requirements

- Do NOT log the Authorization header at any log level.
- Do NOT log decoded database credentials (username or password).
- Do NOT log the value of `extendedProperty` — it may contain sensitive data.
- Do NOT expose driver error messages, SQL errors, or stack traces in responses.
- Sanitize all user-supplied identifiers (schema names, object names) before
  using them in SQL queries.
- Validate all inputs at the DTO layer before they reach the service layer.
- Return generic error messages to the client; log detailed errors server-side only.

---

## Architecture constraints

- Expose functionality via a single POST REST endpoint.
- Controllers must implement an OpenAPI-annotated interface (ExtractionApi).
  OpenAPI annotations belong in the interface and DTOs only — never in the
  controller class itself.
- Use a strategy pattern (or equivalent) for multi-engine DDL extraction.
- DatabaseType must be an enum with driverClass, defaultPort, and dialect fields.
- DdlObjectType must be an enum: TABLE, VIEW, INDEX, PROCEDURE, FUNCTION, SEQUENCE.
- Lombok usage:
  * @RequiredArgsConstructor on all Spring-managed classes
  * @NoArgsConstructor + @AllArgsConstructor on all DTOs
  * @Autowired is forbidden on fields and constructors

---

## Design notes

- Azure SQL and SQL Server use the same JDBC driver. The DatabaseType enum must
  have both entries (AZURE_SQL and SQL_SERVER), but they must resolve to the
  same DdlExtractor bean. Use a qualifier or a registry map to handle this.
- Design for extensibility: adding a new database engine in the future should
  require only adding a new DatabaseType entry and a new DdlExtractor implementation,
  with no changes to service or controller code.
- projectDir is a server-side path. Do not attempt to validate its existence at
  the DTO layer — validate it at the service layer before writing files, and
  return a clear error if the path is not writable.
- `extendedProperty` is applied exclusively via `java.util.Properties` when
  creating the JDBC connection inside `JdbcConnectionFactory`. It must never be
  appended to the JDBC URL string. Parsing must split on the first `=` only
  (use `split("=", 2)`) so that values containing `=` are preserved correctly.
  `JdbcConnectionFactory` must treat this field as optional: when null or absent,
  connection creation proceeds normally without modification.