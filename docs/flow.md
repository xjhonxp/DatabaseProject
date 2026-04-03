GOAL:
Build databaseProject following enterprise-grade practices using Spring Boot and Clean Architecture.
All database engines defined in @docs/project.md must be implemented in the same delivery — no incremental approach.

---

GLOBAL RULES:

- Always use @docs/project.md as the source of truth
- Do NOT skip steps
- Do NOT generate code before architecture is defined (steps 1–4 must complete first)
- Ask questions if requirements are unclear before proceeding
- Do NOT use emojis in source code or comments
- Each step must save its output to the path indicated in SAVE OUTPUT TO before proceeding
- The next step must read its input from the saved file, not from memory or conversation context
- Partial implementations are not acceptable — all engines must be covered in step 5

---

DIRECTORY CONVENTIONS:

Documentation outputs  → docs/generated/
Java source code       → src/main/java/com/example/ddlextractor/
Test source code       → src/test/java/com/example/ddlextractor/

Do NOT place generated documentation inside src/.
Do NOT place source code inside docs/.

---

STEPS:

1. Use skill: ask-questions-if-underspecified
   INPUT:
   @docs/project.md

   FOCUS ON:
   - Any ambiguity in supported database engines (especially Azure SQL vs SQL Server)
   - The HTTP response contract (what does the endpoint return on success/failure?)
   - Whether projectDir is a server-side path or configurable output destination
   - Any missing field validations not covered by the spec
   - Behavior of extendedProperty when value contains an = character

   OUTPUT:
   - List of clarifying questions with assumed answers for each
   - If all requirements are already clear, state that explicitly
   - Do NOT proceed to step 2 until this output is saved

   SAVE OUTPUT TO: docs/generated/01-questions.md

---

2. Use skill: planner
   INPUT:
   @docs/project.md
   @docs/generated/01-questions.md

   OUTPUT:
   Ordered implementation plan listing:
   - Use cases with their input and output ports
   - Port interfaces (in and out) to create
   - Infrastructure adapters (one per database engine)
   - DTO definitions with field list
   - Security components and their responsibilities
   - Configuration classes needed

   SAVE OUTPUT TO: docs/generated/02-plan.md

---

3. Use skill: create-specification
   INPUT:
   @docs/project.md
   @docs/generated/02-plan.md

   OUTPUT:
   Technical specification document including:
   - Full package structure with one line of responsibility per package
   - Class names and layer assignment for every component in the plan
   - Interface contracts (port method signatures with parameter and return types)
   - REST endpoint contract (method, path, headers, request body, all response shapes)
   - Strategy pattern design: how DatabaseType resolves to DdlExtractor at runtime
   - extendedProperty parsing contract: split on first = only, inject into Properties
   - File generation contract: folder naming, file naming, one file per object rule

   SAVE OUTPUT TO: docs/generated/03-specification.md

---

4. Use skill: java-architect
   INPUT:
   @docs/project.md
   @docs/generated/03-specification.md

   OUTPUT:
   Complete architecture definition including:
   - Full package layout (every package listed, one line description each)
   - All interface declarations with method signatures (ports in and out)
   - All enum definitions with their fields and values:
       * DatabaseType (driverClass, defaultPort, dialect) — all 5 engines
       * DdlObjectType (TABLE, VIEW, INDEX, PROCEDURE, FUNCTION, SEQUENCE)
   - Dependency graph: which class depends on which interface
   - List of Spring beans and their injection points
   - Spring Boot configuration classes with their @Bean method signatures

   Do NOT generate method bodies or implementation code.
   Interfaces and signatures only.

   SAVE OUTPUT TO: docs/generated/04-architecture.md

---

5. Use skill: java-springboot
   INPUT:
   @docs/project.md
   @docs/generated/04-architecture.md

   IMPLEMENT ALL of the following in a single delivery.
   Place every file in its correct package under src/main/java/com/example/ddlextractor/.

   Classes to generate:
   - DatabaseType enum (driverClass, defaultPort, dialect)
   - DdlObjectType enum (TABLE, VIEW, INDEX, PROCEDURE, FUNCTION, SEQUENCE)
   - ExtractionInputPort interface (in port)
   - DdlExtractor interface (out port)
   - FileWriter interface (out port)
   - CredentialExtractor interface (out port)
   - ExtractDatabaseStructureUseCase (implements ExtractionInputPort)
   - JdbcConnectionFactory (creates connections; applies extendedProperty via Properties)
   - JdbcTemplate (executes queries; only class touching PreparedStatement and ResultSet)
   - PostgresDdlExtractor (implements DdlExtractor)
   - OracleDdlExtractor (implements DdlExtractor)
   - SqlServerDdlExtractor (implements DdlExtractor — handles SQL_SERVER and AZURE_SQL)
   - MariaDbDdlExtractor (implements DdlExtractor)
   - LocalFileWriter (implements FileWriter)
   - BasicAuthCredentialExtractor (implements CredentialExtractor)
   - ExtractionApi interface (OpenAPI-annotated — in api package)
   - ExtractionController (implements ExtractionApi — delegates only, no logic)
   - ExtractionRequestDto (@Valid, Bean Validation, includes extendedProperty)
   - ExtractionResponseDto (matches success response shape in project.md)
   - ErrorResponseDto (matches validation and connection error shapes in project.md)
   - ExtractorRegistryConfig (@Bean Map<DatabaseType, DdlExtractor>)
   - SecurityConfig (Basic Auth, credential logging disabled)
   - GlobalExceptionHandler (@RestControllerAdvice)

   CODING CONSTRAINTS (enforced for every class generated):
   - @RequiredArgsConstructor on all Spring-managed classes
   - @NoArgsConstructor + @AllArgsConstructor + @Data on all DTOs
   - @Autowired is forbidden on fields and constructors
   - OpenAPI annotations in ExtractionApi and DTOs only — never in controllers
   - Connection, PreparedStatement, ResultSet only inside JdbcConnectionFactory and JdbcTemplate
   - No String.equals() or String.contains() for engine comparison — use DatabaseType enum
   - No driver class name literals outside DatabaseType enum
   - Never log Authorization header, decoded credentials, or extendedProperty value
   - Sanitize schema, table, and object names before SQL interpolation
   - extendedProperty: parse with split("=", 2), inject into java.util.Properties only

   Source code goes to: src/main/java/com/example/ddlextractor/

---

6. Use skill: clean-code
   INPUT:
   All classes generated in step 5 under src/main/java/com/example/ddlextractor/

   REVIEW FOR:
   - Single Responsibility violations (any class doing more than one thing)
   - Abstraction leaks (infrastructure types or Spring annotations in domain or application layer)
   - Hardcoded strings that should be constants or enum values
   - Missing null checks or input sanitization
   - Method length (flag any method over 30 lines for extraction)
   - Naming clarity (rename anything that does not clearly express its intent)
   - extendedProperty: confirm split("=", 2) is used and value is never logged

   OUTPUT:
   - Numbered list of issues found, each with: class name, line range, description, severity
   - Corrected version of each affected class (full class, not snippets)
   - Classes with no issues: list their names and state "no issues found"

   SAVE OUTPUT TO: docs/generated/05-review.md
   Update affected source files in place under src/main/java/.

---

7. Use skill: java-junit
   INPUT:
   @docs/generated/05-review.md
   All corrected classes under src/main/java/com/example/ddlextractor/

   GENERATE UNIT TESTS FOR:
   - ExtractDatabaseStructureUseCase
       * Happy path: valid request, all engines, files written
       * Missing extendedProperty: connection created without extra property
       * Present extendedProperty: property injected into connection Properties
       * Unsupported dbType: throws UnsupportedDatabaseTypeException
       * Connection failure: throws DatabaseConnectionException
       * File write failure: throws FileWriteException
   - BasicAuthCredentialExtractor
       * Valid header: credentials extracted correctly
       * Missing header: throws MissingCredentialsException
       * Malformed base64: throws MissingCredentialsException
       * Header without colon separator: throws MissingCredentialsException
   - DatabaseType enum
       * Each entry has non-blank driverClass
       * Each entry has valid port (1–65535)
       * AZURE_SQL and SQL_SERVER share same driverClass
   - ExtractionRequestDto validation
       * extendedProperty null: valid
       * extendedProperty "key=value": valid
       * extendedProperty "key=val=ue": valid (= in value is allowed)
       * extendedProperty "noEquals": invalid
       * extendedProperty "=missingKey": invalid
       * extendedProperty blank string: invalid
   - Each DdlExtractor (Postgres, Oracle, SqlServer, MariaDb)
       * Mock JdbcTemplate, verify correct SQL issued per object type
   - LocalFileWriter
       * Mock filesystem, verify folder structure matches project.md spec
       * Schema with special characters: sanitized correctly

   REQUIREMENTS:
   - JUnit 5 + Mockito only — no Spring context (@ExtendWith(MockitoExtension.class))
   - Test class name: <ClassName>Test
   - Method name: <methodName>_<scenario>_<expectedOutcome>
   - One scenario per test method — no multiple assertions testing different behaviors

   Test files go to: src/test/java/com/example/ddlextractor/

---

8. Use skill: scoutqa-test
   INPUT:
   @docs/project.md
   @docs/generated/04-architecture.md
   All source files under src/main/java/
   All unit tests under src/test/java/

   GENERATE:

   Integration tests (@SpringBootTest + Testcontainers):
   - PostgreSQL container: full extraction flow, verify files written to projectDir
   - MariaDB container: full extraction flow, verify files written to projectDir
   - Both: verify response body matches success shape defined in project.md
   - Both: verify extendedProperty is accepted and does not appear in response or logs

   Security test scenarios:
   - Request with no Authorization header → 401, no stack trace in body
   - Request with invalid base64 in Authorization → 401, no credentials in body
   - Request with valid auth but wrong db credentials → 502, no credentials in body
   - Verify Authorization header does not appear in any log output during any test
   - Verify extendedProperty value does not appear in any log output

   Edge cases per engine:
   - Schema with zero objects of one type: subfolder not created
   - Schema name containing spaces or special characters: files created with sanitized names
   - Object name containing special characters: file created, DDL correct
   - Database with 0 schemas: response returns empty schemas list, status SUCCESS
   - Port out of range in request: 400 VALIDATION_ERROR before any DB call

   File system edge cases:
   - projectDir does not exist: service attempts creation or returns clear error
   - projectDir exists but is not writable: FileWriteException mapped to 500
   - projectDir is a relative path: 400 VALIDATION_ERROR at DTO layer

   Test files go to: src/test/java/com/example/ddlextractor/