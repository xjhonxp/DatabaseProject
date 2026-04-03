# Refactor: align codebase to defined architecture

Read @AGENTS.md and @docs/project.md in full before doing anything.
They are the source of truth for the target structure and coding standards.
Do NOT modify any file until phase 1 is complete and saved.

---

## Phase 1 — Map the current state (read only, no changes)

Scan the entire codebase under src/. For every existing class, record:
- Current fully-qualified class name
- Current responsibility (one sentence)
- Target package according to AGENTS.md
- Action required: MOVE / RENAME / SPLIT / REWRITE / DELETE / KEEP

Save the result as a mapping table to:
SAVE OUTPUT TO: docs/generated/refactor-01-mapping.md

Do NOT touch any source file during this phase.
Do NOT proceed to phase 2 until the mapping file is saved.

---

## Phase 2 — Identify gaps (read only, no changes)

Read @docs/generated/refactor-01-mapping.md.

Compare the existing classes against the target class list defined in AGENTS.md
(section 5, java-springboot step). Identify:

- Classes that exist and map cleanly to the target structure (KEEP or MOVE)
- Classes that exist but must be split because they violate Single Responsibility
- Classes that exist but must be rewritten because they violate AGENTS.md constraints
- Classes that are missing entirely and must be created from scratch

Also identify any violations already present in the codebase:
- @Autowired on fields or constructors
- OpenAPI annotations inside controllers
- Raw Connection / PreparedStatement / ResultSet outside infrastructure layer
- String comparisons for database engine resolution
- Authorization header or credentials being logged

Save the result to:
SAVE OUTPUT TO: docs/generated/refactor-02-gaps.md

Do NOT touch any source file during this phase.
Do NOT proceed to phase 3 until the gaps file is saved.

---

## Phase 3 — Execute the refactor (all changes in one pass)

Read @docs/generated/refactor-01-mapping.md and @docs/generated/refactor-02-gaps.md
before writing a single line of code.

Execute all changes in this exact order to minimize broken intermediate states:

### 3.1 — Domain layer first (no dependencies on anything)
- Create or move DatabaseType enum to domain package
- Create or move DdlObjectType enum to domain package
- Ensure zero Spring or JDBC imports in domain classes
- Compile check: domain package must compile in isolation

### 3.2 — Port interfaces second (depend only on domain)
- Create or move ExtractionInputPort to port/in
- Create or move DdlExtractor, FileWriter, CredentialExtractor to port/out
- Interfaces must use only domain types and Java primitives in signatures
- No Spring annotations in any port interface

### 3.3 — Application layer third (depends on ports, not infrastructure)
- Create or move ExtractDatabaseStructureUseCase to application/usecase
- Must implement ExtractionInputPort
- Must inject port interfaces only — no concrete infrastructure classes
- Must handle extendedProperty: pass it through to the connection port as-is
- No org.springframework imports except @Service and @RequiredArgsConstructor

### 3.4 — Infrastructure layer fourth (implements ports)
- Move or rewrite JdbcConnectionFactory:
    * Must be the only class creating JDBC connections
    * Must apply extendedProperty via java.util.Properties using split("=", 2)
    * Must never log the extendedProperty value
- Move or rewrite JdbcTemplate:
    * Must be the only class referencing PreparedStatement and ResultSet directly
    * Must accept a ResultSetExtractor<T> functional interface for result mapping
- Move or rewrite each DdlExtractor implementation:
    * PostgresDdlExtractor, OracleDdlExtractor, SqlServerDdlExtractor, MariaDbDdlExtractor
    * SqlServerDdlExtractor handles both SQL_SERVER and AZURE_SQL
    * Each must use JdbcTemplate — no raw JDBC
- Move or rewrite LocalFileWriter (implements FileWriter)
- Move or rewrite BasicAuthCredentialExtractor (implements CredentialExtractor):
    * Must never log the Authorization header or decoded credentials

### 3.5 — DTOs and API interface (no logic, annotations only)
- Move or rewrite ExtractionRequestDto:
    * Must include extendedProperty as optional String field
    * Bean Validation: @Nullable, and when present must match pattern ^\S+=.*$
    * @NoArgsConstructor + @AllArgsConstructor + @Data
- Move or rewrite ExtractionResponseDto and ErrorResponseDto
- Move or rewrite ExtractionApi interface:
    * All @Operation, @ApiResponse, @Tag annotations here only
    * No annotations of any kind in ExtractionController

### 3.6 — Controller (last, depends on everything)
- Move or rewrite ExtractionController:
    * Must implement ExtractionApi
    * Must contain only delegation to ExtractionInputPort
    * No business logic, no OpenAPI annotations, no JDBC references

### 3.7 — Configuration and wiring
- Move or rewrite ExtractorRegistryConfig:
    * @Bean Map<DatabaseType, DdlExtractor> wiring all 5 engines
    * AZURE_SQL and SQL_SERVER must map to the same SqlServerDdlExtractor bean
- Move or rewrite SecurityConfig (Basic Auth, no credential logging)
- Move or rewrite GlobalExceptionHandler (@RestControllerAdvice):
    * Must map each custom exception to the response shape in project.md
    * Must never expose internal exception messages in response body

### 3.8 — Delete dead code
- Delete any class that no longer has a role after the refactor
- Delete any package that is now empty
- Do NOT leave commented-out code or TODO placeholders

---

## Phase 4 — Verify

After all changes in phase 3 are complete:

1. Confirm the package structure matches AGENTS.md exactly.
   List any deviation and explain why it was intentional.

2. Run a static check on every modified class and confirm:
   - No @Autowired on fields or constructors anywhere
   - No OpenAPI annotations outside ExtractionApi interface and DTOs
   - No Connection / PreparedStatement / ResultSet outside infrastructure/jdbc
   - No String.equals() or String.contains() for DatabaseType comparison
   - No log statements containing "Authorization", "password", "credential",
     or "extendedProperty" value

3. Confirm all existing tests still compile and pass.
   If any test breaks due to a package or class rename, update the import
   in the test file — do NOT change the test logic.

4. If any test was previously testing a class that was split or renamed,
   update the test class to reflect the new class name and package.
   Do NOT delete tests.

Save the verification report to:
SAVE OUTPUT TO: docs/generated/refactor-03-verification.md