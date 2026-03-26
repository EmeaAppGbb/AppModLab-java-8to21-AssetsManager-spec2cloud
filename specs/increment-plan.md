# Modernization Increment Plan

> Generated from `specs/assessment/modernization.md` — 28 findings across 6 categories.
> ADR constraints: ADR-001 (Java 21), ADR-002 (Spring Boot 3.4.x), ADR-003 (Shared Maven module).

## Dependency Graph

```
mod-001 ──┐
mod-002 ──┼──→ mod-003 ──┬──→ mod-004
          │              └──→ mod-005 ──→ mod-006 ──┬──→ mod-007 ──┐
          │                                         ├──→ mod-008   │
          │                                         ├──→ mod-009   │
          │                                         ├──→ mod-010   ├──→ mod-012 ──┬──→ mod-014
          │                                         ├──→ mod-011   ├──→ mod-013 ──┘
          │                                         ├──→ mod-015   │
          │                                         ├──→ mod-016   │
          │                                         └──→ mod-017   │
          │                                                        └──→ mod-011 (also dep on 012,013)
```

### Topological Order (Tiers)

| Tier | Increments | Can Run In Parallel |
|------|-----------|---------------------|
| 0 | mod-001, mod-002 | Yes |
| 1 | mod-003 | — |
| 2 | mod-004, mod-005 | Yes |
| 3 | mod-006 | — |
| 4 | mod-007, mod-008, mod-009, mod-010, mod-015, mod-016, mod-017 | Yes |
| 5 | mod-012, mod-013 | Yes |
| 6 | mod-011, mod-014 | Yes |

---

## mod-001: Fix Missing @Bean on Web S3Client

- **Type:** modernization
- **Assessment Findings:** A1 (Critical)
- **Scope:** Add `@Bean` annotation to the `s3Client()` method in `web/../config/AwsS3Config.java`. No other changes. Worker module's `AwsS3Config` already has `@Bean` and is unaffected.
- **Acceptance Criteria:**
  - [ ] `web/../config/AwsS3Config.java` `s3Client()` method has `@Bean` annotation
  - [ ] Web module compiles without errors
  - [ ] Application starts in non-dev profile without `NoSuchBeanDefinitionException` for `S3Client`
  - [ ] Worker module unaffected (no changes)
- **Test Strategy:**
  - Build both modules: `./mvnw clean compile`
  - Verify existing 4 worker tests still pass: `./mvnw test -pl worker`
  - Manual: start web module in non-dev profile, verify S3Client bean exists in context
- **Gherkin Deltas:**
  - New: none
  - Modified: none
  - Regression: 4 existing worker tests must still pass
- **Dependencies:** none (walking skeleton)
- **Rollback Plan:** Remove `@Bean` annotation from `s3Client()` method.
- **Risk:** Low — single annotation addition, no behavioral change for dev profile

---

## mod-002: Externalize Credentials and Harden Configuration

- **Type:** modernization
- **Assessment Findings:** S1 (Critical), S2 (Critical), A5 (Medium), CI2 (Medium)
- **Scope:** Replace all hardcoded credentials in `application.properties` (both modules) with environment variable placeholders. Standardize AWS property names (`aws.accessKey` → `aws.accessKeyId`). Set `show-sql=false`. No code changes — only `application.properties` files in `web/` and `worker/`.
- **Acceptance Criteria:**
  - [ ] No plain-text credentials remain in any `application.properties` file
  - [ ] All credentials use `${ENV_VAR:default}` pattern (default only for non-sensitive values)
  - [ ] AWS property names consistent between web and worker (`aws.accessKeyId`, `aws.secretKey`, `aws.region`, `aws.s3.bucket`)
  - [ ] `web/../config/AwsS3Config.java` updated to reference `aws.accessKeyId` (matching worker)
  - [ ] `spring.jpa.show-sql=false` in both modules
  - [ ] Both modules compile and start with environment variables provided
- **Test Strategy:**
  - Build both modules: `./mvnw clean compile`
  - Verify existing 4 worker tests still pass
  - Manual: start both modules with env vars set, verify they connect to PostgreSQL and RabbitMQ
- **Gherkin Deltas:**
  - New: none
  - Modified: none
  - Regression: 4 existing worker tests must still pass unchanged
- **Dependencies:** none
- **Rollback Plan:** Restore original `application.properties` from git. Revert `AwsS3Config` property name.
- **Risk:** Low — config-only changes. Risk is only if environment variables are not set at runtime.

---

## mod-003: Create Shared Maven Module for Common Code

- **Type:** modernization
- **Assessment Findings:** A2 (High)
- **ADR:** ADR-003 (Create shared `common` Maven module)
- **Scope:** Create a new `common` Maven module. Move `ImageMetadata`, `ImageProcessingMessage`, `ImageMetadataRepository`, and `StorageUtil` into `common`. Update imports in `web` and `worker`. Remove duplicated classes from both modules. Update parent POM `<modules>` and child POMs `<dependencies>`.
- **Acceptance Criteria:**
  - [ ] `common/` module exists with `pom.xml`, `src/main/java/` structure
  - [ ] `ImageMetadata`, `ImageProcessingMessage`, `ImageMetadataRepository`, `StorageUtil` live in `common` only
  - [ ] No duplicate model/repository classes remain in `web` or `worker`
  - [ ] Parent POM lists `common` in `<modules>`
  - [ ] `web/pom.xml` and `worker/pom.xml` declare dependency on `common`
  - [ ] Both modules compile: `./mvnw clean compile`
  - [ ] Existing worker tests pass: `./mvnw test -pl worker`
- **Test Strategy:**
  - Full build: `./mvnw clean compile`
  - Run worker tests: `./mvnw test -pl worker`
  - Manual: start both modules, verify file upload/list/thumbnail pipeline works
- **Gherkin Deltas:**
  - New: none
  - Modified: none
  - Regression: 4 existing worker tests must pass (imports may need updating)
- **Dependencies:** mod-001, mod-002
- **Rollback Plan:** Delete `common/` module. Restore duplicated classes in `web` and `worker` from git. Revert POM changes.
- **Risk:** Medium — structural refactoring across 3 modules. Risk of missed import or package scan issue.

---

## mod-004: Standardize Logging Across All Modules

- **Type:** modernization
- **Assessment Findings:** P3 (High), P6 (Medium), P7 (Low)
- **Scope:** Replace all `System.out.printf` calls in `WebMvcConfig.java` with SLF4J logging. Fix string concatenation in `log.error()` calls (use parameterized logging). Standardize all classes to use Lombok `@Slf4j` annotation instead of manual `LoggerFactory.getLogger()`. Affects: `web/../config/WebMvcConfig.java`, `web/../service/LocalFileStorageService.java`, `web/../service/BackupMessageProcessor.java`, `worker/../service/AbstractFileProcessingService.java`.
- **Acceptance Criteria:**
  - [ ] Zero `System.out.printf` or `System.out.println` calls in any Java file
  - [ ] All logging uses SLF4J via Lombok `@Slf4j`
  - [ ] No string concatenation in log method arguments (all parameterized: `log.error("{}", var, e)`)
  - [ ] Both modules compile
  - [ ] Existing worker tests pass
- **Test Strategy:**
  - Grep for `System.out` — must return zero matches in `src/main/java`
  - Grep for `LoggerFactory.getLogger` — must return zero matches (all replaced by `@Slf4j`)
  - Build: `./mvnw clean compile`
  - Tests: `./mvnw test -pl worker`
- **Gherkin Deltas:**
  - New: none
  - Modified: none
  - Regression: 4 existing worker tests must pass unchanged
- **Dependencies:** mod-003 (shared module exists, some files may have moved)
- **Rollback Plan:** Revert all logging changes from git diff.
- **Risk:** Low — logging is non-functional. No behavioral change.

---

## mod-005: Upgrade Java 8 to Java 21

- **Type:** modernization
- **Assessment Findings:** D2 (Critical), D5 (High)
- **ADR:** ADR-001 (Java 21 LTS)
- **Scope:** Change `<java.version>` from `8` to `21` in parent POM. Replace `javax.annotation.PostConstruct` with `jakarta.annotation.PostConstruct` (removed in Java 11, absent in Java 17+). Update Maven compiler plugin settings if needed. Verify compilation with JDK 21. No feature changes — language level only.
- **Acceptance Criteria:**
  - [ ] Parent `pom.xml` has `<java.version>21</java.version>`
  - [ ] No `javax.annotation` imports remain in any Java file
  - [ ] All `@PostConstruct` uses import from `jakarta.annotation` (or Spring's `@PostConstruct`)
  - [ ] `./mvnw clean compile` succeeds on JDK 21
  - [ ] Existing worker tests pass on JDK 21
  - [ ] Maven wrapper configured for JDK 21 compatibility
- **Test Strategy:**
  - Build: `./mvnw clean compile` (on JDK 21)
  - Tests: `./mvnw test -pl worker`
  - Verify: `javac -version` confirms JDK 21
- **Gherkin Deltas:**
  - New: none
  - Modified: none
  - Regression: 4 existing worker tests must pass on Java 21
- **Dependencies:** mod-003 (shared module exists — change `@PostConstruct` imports once in common)
- **Rollback Plan:** Change `<java.version>` back to `8`. Restore `javax.annotation` imports. Build with JDK 8.
- **Risk:** Low — codebase uses no removed APIs (no Nashorn, no Security Manager, no sun.misc). The only breaking change is `javax.annotation` removal.

---

## mod-006: Upgrade Spring Boot 2.7 to 3.4.x

- **Type:** modernization
- **Assessment Findings:** D1 (Critical), D3 (High), D4 (High), P1 (High), P2 (High)
- **ADR:** ADR-002 (Spring Boot 3.4.x)
- **Scope:** Upgrade `spring-boot-starter-parent` from `2.7.18` to `3.4.x`. Migrate all `javax.persistence` → `jakarta.persistence`, `javax.servlet` → `jakarta.servlet`. Replace `WebMvcConfigurerAdapter` with `WebMvcConfigurer` interface. Replace `HandlerInterceptorAdapter` with `HandlerInterceptor` interface. Remove `@SuppressWarnings("deprecation")`. Update Hibernate dialect property (auto-detected in SB3). Verify all dependencies are SB3-compatible.
- **Acceptance Criteria:**
  - [ ] Parent POM references Spring Boot 3.4.x
  - [ ] Zero `javax.persistence` imports remain — all replaced with `jakarta.persistence`
  - [ ] Zero `javax.servlet` imports remain — all replaced with `jakarta.servlet`
  - [ ] `WebMvcConfig` implements `WebMvcConfigurer` (not extends adapter)
  - [ ] `FileOperationLoggingInterceptor` implements `HandlerInterceptor` (not extends adapter)
  - [ ] No `@SuppressWarnings("deprecation")` related to removed adapters
  - [ ] `hibernate.dialect` property removed (auto-detected in SB3)
  - [ ] `./mvnw clean compile` succeeds for all modules
  - [ ] Existing worker tests pass
  - [ ] Application starts and serves pages
- **Test Strategy:**
  - Build: `./mvnw clean compile`
  - Tests: `./mvnw test -pl worker`
  - Grep: `javax.persistence` → 0 matches; `javax.servlet` → 0 matches
  - Manual: start both modules, verify file upload/list/view/delete works
- **Gherkin Deltas:**
  - New: none
  - Modified: none
  - Regression: 4 existing worker tests must pass (update test imports to jakarta if needed)
- **Dependencies:** mod-005 (Java 21 required for Spring Boot 3)
- **Rollback Plan:** Revert parent POM to `2.7.18`. Restore all `javax` imports. Restore adapter classes. Rebuild with JDK 8.
- **Risk:** High — largest single increment. javax→jakarta affects all modules. Spring Boot 3 may change auto-configuration behavior. Mitigated by: small codebase, well-documented migration path, OpenRewrite tooling available.

---

## mod-007: Optimize Database Queries — Eliminate findAll() Anti-Pattern

- **Type:** modernization
- **Assessment Findings:** A3 (High), A4 (High)
- **Scope:** Add `Optional<ImageMetadata> findByS3Key(String s3Key)` to `ImageMetadataRepository` in the `common` module. Refactor `AwsS3Service.listObjects()` to pre-fetch metadata once and use a `Map` for O(1) lookups instead of O(n²). Refactor `S3FileProcessingService.uploadThumbnail()` and `AwsS3Service.deleteObject()` to use `findByS3Key()` instead of `findAll().stream().filter()`.
- **Acceptance Criteria:**
  - [ ] `ImageMetadataRepository` has `findByS3Key(String s3Key)` method
  - [ ] Zero instances of `findAll().stream().filter()` pattern in service layer
  - [ ] `AwsS3Service.listObjects()` makes at most 1 DB query (not N)
  - [ ] `S3FileProcessingService.uploadThumbnail()` uses `findByS3Key()`
  - [ ] `AwsS3Service.deleteObject()` uses `findByS3Key()`
  - [ ] All existing tests pass
  - [ ] Application behavior unchanged (same responses, same data)
- **Test Strategy:**
  - Run all tests: `./mvnw test`
  - Manual: upload file, list files, view file, delete file — same behavior as before
- **Gherkin Deltas:**
  - New: none (performance improvement, no behavioral change)
  - Modified: none
  - Regression: all existing tests must pass unchanged
- **Dependencies:** mod-006 (jakarta namespace for JPA annotations)
- **Rollback Plan:** Revert repository and service changes from git diff.
- **Risk:** Low — internal refactoring only. No API or behavioral changes. JPA query methods are well-tested Spring Data patterns.

---

## mod-008: Add Input Validation on Path Variables

- **Type:** modernization
- **Assessment Findings:** S3 (High)
- **Scope:** Add input validation to `S3Controller` for all `@PathVariable String key` parameters. Reject keys containing `..`, `/`, `\`, or null/empty values. Return 400 Bad Request for invalid keys. Affects `viewObjectPage()`, `viewObject()`, and `deleteObject()` in `S3Controller.java`.
- **Acceptance Criteria:**
  - [ ] All `@PathVariable` handlers validate the `key` parameter
  - [ ] Keys containing `..`, `/`, or `\` are rejected with HTTP 400
  - [ ] Empty or null keys are rejected with HTTP 400
  - [ ] Valid keys (alphanumeric, hyphens, underscores, dots) are accepted
  - [ ] All existing tests pass
- **Test Strategy:**
  - Run all tests: `./mvnw test`
  - Manual: attempt path traversal (e.g., `../../../etc/passwd`) — expect 400
  - Manual: normal file operations — unchanged behavior
- **Gherkin Deltas:**
  - New: `Scenario: Reject path traversal in file key` — validates security boundary
  - Modified: none
  - Regression: all existing tests must pass
- **Dependencies:** mod-006 (Spring Boot 3 validation patterns)
- **Rollback Plan:** Remove validation logic from controller. Revert to accepting all keys.
- **Risk:** Low — additive validation only. Existing valid keys continue to work.

---

## mod-009: Add Transaction Management for Multi-Step Operations

- **Type:** modernization
- **Assessment Findings:** A6 (Medium)
- **Scope:** Add `@Transactional` to `AwsS3Service.uploadObject()` and `AwsS3Service.deleteObject()` to ensure DB operations are atomic. Implement compensating actions: if DB save fails after S3 upload, delete the S3 object. Affects only `web/../service/AwsS3Service.java`.
- **Acceptance Criteria:**
  - [ ] `uploadObject()` is `@Transactional` — DB rollback on failure
  - [ ] `deleteObject()` is `@Transactional` — DB rollback on failure
  - [ ] If S3 upload succeeds but DB save fails, S3 object is cleaned up
  - [ ] All existing tests pass
  - [ ] Application behavior unchanged for happy path
- **Test Strategy:**
  - Run all tests: `./mvnw test`
  - Manual: upload file — verify both S3 and DB are updated
  - Manual: simulate DB failure (e.g., stop PostgreSQL during upload) — verify no orphan S3 objects
- **Gherkin Deltas:**
  - New: none (infrastructure hardening, no visible behavioral change)
  - Modified: none
  - Regression: all existing tests must pass
- **Dependencies:** mod-006 (Spring Boot 3 transaction management)
- **Rollback Plan:** Remove `@Transactional` and compensating logic. Revert to original code.
- **Risk:** Medium — transaction boundaries can cause unexpected behavior (lazy loading, proxy issues). Test thoroughly.

---

## mod-010: Update AWS SDK to Latest Version

- **Type:** modernization
- **Assessment Findings:** D6 (Medium)
- **Scope:** Update `<aws-sdk.version>` from `2.25.13` to `2.42.21` in both `web/pom.xml` and `worker/pom.xml` (or centralize in parent/common POM). No code changes expected — AWS SDK v2 has stable APIs.
- **Acceptance Criteria:**
  - [ ] AWS SDK version is `2.42.21` (or latest 2.x) in all POMs
  - [ ] Both modules compile without errors
  - [ ] All existing tests pass
  - [ ] S3 operations work unchanged
- **Test Strategy:**
  - Build: `./mvnw clean compile`
  - Run all tests: `./mvnw test`
  - Manual: upload and download a file via S3 — verify unchanged behavior
- **Gherkin Deltas:**
  - New: none
  - Modified: none
  - Regression: all existing tests must pass
- **Dependencies:** mod-006 (Spring Boot 3 may manage SDK version)
- **Rollback Plan:** Revert `<aws-sdk.version>` to `2.25.13`.
- **Risk:** Low — AWS SDK v2 has stable, backward-compatible APIs. Drop-in version bump.

---

## mod-011: Adopt Modern Java 21 Idioms

- **Type:** modernization
- **Assessment Findings:** P4 (Medium), P5 (Medium)
- **Scope:** Convert `ImageProcessingMessage` and `S3StorageItem` DTOs from Lombok `@Data` classes to Java `record` types. Replace if-else chain in `WebMvcConfig.determineFileOperation()` with a switch expression. Apply pattern matching where beneficial. Affects: `common/../model/ImageProcessingMessage.java`, `web/../model/S3StorageItem.java`, `web/../config/WebMvcConfig.java`.
- **Acceptance Criteria:**
  - [ ] `ImageProcessingMessage` is a Java `record`
  - [ ] `S3StorageItem` is a Java `record`
  - [ ] `determineFileOperation()` uses a switch expression
  - [ ] No Lombok `@Data`/`@AllArgsConstructor`/`@NoArgsConstructor` on converted records
  - [ ] All existing tests pass
  - [ ] RabbitMQ serialization/deserialization works unchanged (Jackson supports records)
- **Test Strategy:**
  - Build: `./mvnw clean compile`
  - Run all tests: `./mvnw test`
  - Manual: upload file, verify RabbitMQ message processing works (record serialization)
- **Gherkin Deltas:**
  - New: none
  - Modified: none
  - Regression: all existing tests must pass (verify Jackson serialization of records)
- **Dependencies:** mod-006 (Spring Boot 3), mod-007 (query changes may affect model usage)
- **Rollback Plan:** Revert records to Lombok `@Data` classes. Revert switch expression to if-else.
- **Risk:** Medium — Java records change equals/hashCode semantics and require Jackson configuration for deserialization (no-arg constructor absent). Test RabbitMQ message flow carefully.

---

## mod-012: Add Comprehensive Unit Tests for Web Module

- **Type:** modernization
- **Assessment Findings:** T1 (Critical)
- **Scope:** Create unit tests for all web module services and controllers: `AwsS3Service`, `LocalFileStorageService`, `S3Controller`, `HomeController`, `BackupMessageProcessor`. Use Mockito for mocking external dependencies (S3Client, RabbitTemplate, repositories). Target: every public method in every service and controller class has at least one test.
- **Acceptance Criteria:**
  - [ ] Test classes exist for: `AwsS3ServiceTest`, `LocalFileStorageServiceTest`, `S3ControllerTest`, `HomeControllerTest`
  - [ ] Every public method in service/controller layer has at least one test
  - [ ] Error scenarios are tested (upload failure, file not found, S3 errors)
  - [ ] All tests pass: `./mvnw test -pl web`
  - [ ] Existing worker tests unaffected
- **Test Strategy:**
  - Unit tests with JUnit 5 + Mockito
  - Spring `@WebMvcTest` for controller tests
  - Aim for >80% line coverage on service layer
- **Gherkin Deltas:**
  - New: Gherkin scenarios describing existing behavior may be generated in a future increment
  - Modified: none
  - Regression: existing worker tests must pass
- **Dependencies:** mod-006 (tests written against Spring Boot 3 / Jakarta namespace), mod-007 (test optimized queries, not broken O(n²) pattern)
- **Rollback Plan:** Delete new test files. No production code changed.
- **Risk:** Low — additive only. No production code changes.

---

## mod-013: Expand Worker Module Unit Tests

- **Type:** modernization
- **Assessment Findings:** T2 (High)
- **Scope:** Add tests for `AbstractFileProcessingService` (thumbnail generation, message acknowledgment, error handling), `LocalFileProcessingService`, and error scenarios in `S3FileProcessingService`. Strengthen existing test assertions. Target: every public/protected method has at least one test.
- **Acceptance Criteria:**
  - [ ] Test classes exist for: `AbstractFileProcessingServiceTest`, `LocalFileProcessingServiceTest`
  - [ ] Existing `S3FileProcessingServiceTest` expanded with error scenario tests
  - [ ] Thumbnail generation tested with real image files (small test images)
  - [ ] Message ack/nack logic tested
  - [ ] All tests pass: `./mvnw test -pl worker`
- **Test Strategy:**
  - JUnit 5 + Mockito for service-level mocking
  - Test image files in `src/test/resources/` for thumbnail generation tests
  - Verify both success and failure message handling paths
- **Gherkin Deltas:**
  - New: none
  - Modified: none
  - Regression: existing 4 worker tests must pass (strengthened, not removed)
- **Dependencies:** mod-006 (tests written against Spring Boot 3), mod-007 (test optimized repository methods)
- **Rollback Plan:** Delete new test files. No production code changed.
- **Risk:** Low — additive only.

---

## mod-014: Add Integration Tests with Testcontainers

- **Type:** modernization
- **Assessment Findings:** T3 (High)
- **Scope:** Add `@SpringBootTest` integration tests using Testcontainers for PostgreSQL and RabbitMQ. Verify the web↔RabbitMQ↔worker pipeline end-to-end: upload a file → message sent to queue → worker processes thumbnail → metadata updated in DB. Use LocalStack or S3 mock for S3 integration tests.
- **Acceptance Criteria:**
  - [ ] Testcontainers dependency added to both module POMs (test scope)
  - [ ] PostgreSQL Testcontainer configured and working
  - [ ] RabbitMQ Testcontainer configured and working
  - [ ] At least one integration test verifies the upload→process→metadata pipeline
  - [ ] All integration tests pass: `./mvnw verify`
  - [ ] Existing unit tests unaffected
- **Test Strategy:**
  - `@SpringBootTest` with Testcontainers for real infrastructure
  - Test the full message flow: upload → queue → process → DB update
  - Use `@DynamicPropertySource` for Testcontainer connection strings
- **Gherkin Deltas:**
  - New: `Scenario: Full upload-to-thumbnail pipeline` — end-to-end verification
  - Modified: none
  - Regression: all unit tests must still pass
- **Dependencies:** mod-012, mod-013 (unit tests provide baseline coverage first)
- **Rollback Plan:** Remove Testcontainers dependencies and integration test files.
- **Risk:** Medium — Testcontainers requires Docker. CI/CD must have Docker-in-Docker or similar.

---

## mod-015: Create CI/CD Pipeline with GitHub Actions

- **Type:** modernization
- **Assessment Findings:** CI1 (High)
- **Scope:** Create `.github/workflows/ci.yml` with: checkout → setup JDK 21 → Maven build → run all tests (unit + integration) → upload test reports. Trigger on push to `main` and pull requests. No deployment step in this increment.
- **Acceptance Criteria:**
  - [ ] `.github/workflows/ci.yml` exists and is valid YAML
  - [ ] Workflow triggers on push to `main` and on PRs
  - [ ] JDK 21 is provisioned
  - [ ] `./mvnw clean verify` runs for all modules
  - [ ] Test results are reported
  - [ ] Workflow passes on current codebase
- **Test Strategy:**
  - Push to branch → verify GitHub Actions workflow triggers and passes
  - Verify test results appear in workflow summary
- **Gherkin Deltas:**
  - New: none (infrastructure, no app behavior change)
  - Modified: none
  - Regression: all tests must pass in CI environment
- **Dependencies:** mod-006 (build against Java 21 / Spring Boot 3)
- **Rollback Plan:** Delete `.github/workflows/ci.yml`.
- **Risk:** Low — additive only. No production code changes.

---

## mod-016: Add Flyway Schema Migrations

- **Type:** modernization
- **Assessment Findings:** S4 (Medium)
- **Scope:** Add Flyway dependency. Create initial migration (`V1__initial_schema.sql`) matching the current Hibernate-generated schema. Change `spring.jpa.hibernate.ddl-auto` from `update` to `validate` in both modules. Future schema changes will use numbered Flyway migrations instead of Hibernate DDL auto-update.
- **Acceptance Criteria:**
  - [ ] `spring-boot-starter-flyway` added to `common` or both module POMs (test scope excluded)
  - [ ] `V1__initial_schema.sql` exists in `common/src/main/resources/db/migration/`
  - [ ] `spring.jpa.hibernate.ddl-auto=validate` in both modules' `application.properties`
  - [ ] Flyway runs on startup and applies migration
  - [ ] Application starts and operates normally
  - [ ] All tests pass
- **Test Strategy:**
  - Start application with empty database → Flyway creates schema
  - Start application with existing database → Flyway validates (no changes)
  - Run all tests: `./mvnw test`
- **Gherkin Deltas:**
  - New: none (infrastructure change, no behavioral impact)
  - Modified: none
  - Regression: all existing tests must pass
- **Dependencies:** mod-006 (Spring Boot 3 Flyway auto-configuration)
- **Rollback Plan:** Remove Flyway dependency. Restore `ddl-auto=update`. Delete migration files.
- **Risk:** Medium — incorrect initial migration SQL could prevent startup. Must exactly match current Hibernate schema. Test against a fresh database.

---

## mod-017: Add API Documentation with Springdoc OpenAPI

- **Type:** modernization
- **Assessment Findings:** DOC1 (Medium)
- **Scope:** Add `springdoc-openapi-starter-webmvc-ui` dependency to the `web` module. Configure OpenAPI metadata (title, description, version). Add `@Operation` and `@ApiResponse` annotations to `S3Controller` endpoints. Swagger UI accessible at `/swagger-ui.html`.
- **Acceptance Criteria:**
  - [ ] Springdoc dependency added to `web/pom.xml`
  - [ ] `/swagger-ui.html` serves Swagger UI
  - [ ] `/v3/api-docs` returns OpenAPI JSON
  - [ ] All controller endpoints appear in Swagger UI with descriptions
  - [ ] All tests pass
- **Test Strategy:**
  - Start web module → navigate to `/swagger-ui.html` → verify UI renders
  - Verify `/v3/api-docs` returns valid OpenAPI JSON
  - Run all tests: `./mvnw test`
- **Gherkin Deltas:**
  - New: `Scenario: Swagger UI is accessible` — documents new endpoint
  - Modified: none
  - Regression: all existing tests must pass
- **Dependencies:** mod-006 (Springdoc for Spring Boot 3)
- **Rollback Plan:** Remove Springdoc dependency and annotations.
- **Risk:** Low — additive only. No changes to existing endpoints.

---

## Summary

| ID | Title | Severity | Findings | Dependencies | Risk |
|----|-------|----------|----------|-------------|------|
| mod-001 | Fix @Bean on Web S3Client | Critical | A1 | none | Low |
| mod-002 | Externalize Credentials | Critical | S1, S2, A5, CI2 | none | Low |
| mod-003 | Create Shared Maven Module | High | A2 | mod-001, mod-002 | Medium |
| mod-004 | Standardize Logging | High | P3, P6, P7 | mod-003 | Low |
| mod-005 | Upgrade Java 8 → 21 | Critical | D2, D5 | mod-003 | Low |
| mod-006 | Upgrade Spring Boot 2.7 → 3.4.x | Critical | D1, D3, D4, P1, P2 | mod-005 | High |
| mod-007 | Optimize Database Queries | High | A3, A4 | mod-006 | Low |
| mod-008 | Add Input Validation | High | S3 | mod-006 | Low |
| mod-009 | Add Transaction Management | Medium | A6 | mod-006 | Medium |
| mod-010 | Update AWS SDK | Medium | D6 | mod-006 | Low |
| mod-011 | Adopt Modern Java Idioms | Medium | P4, P5 | mod-006, mod-007 | Medium |
| mod-012 | Web Module Unit Tests | Critical | T1 | mod-006, mod-007 | Low |
| mod-013 | Worker Module Unit Tests | High | T2 | mod-006, mod-007 | Low |
| mod-014 | Integration Tests (Testcontainers) | High | T3 | mod-012, mod-013 | Medium |
| mod-015 | CI/CD Pipeline | High | CI1 | mod-006 | Low |
| mod-016 | Flyway Schema Migrations | Medium | S4 | mod-006 | Medium |
| mod-017 | API Documentation (Springdoc) | Medium | DOC1 | mod-006 | Low |

### Finding Coverage

All 28 assessment findings are covered:

| Finding | Increment | | Finding | Increment |
|---------|-----------|---|---------|-----------|
| A1 | mod-001 | | P4 | mod-011 |
| S1 | mod-002 | | P5 | mod-011 |
| S2 | mod-002 | | P6 | mod-004 |
| A5 | mod-002 | | P7 | mod-004 |
| CI2 | mod-002 | | A3 | mod-007 |
| A2 | mod-003 | | A4 | mod-007 |
| P3 | mod-004 | | S3 | mod-008 |
| D2 | mod-005 | | A6 | mod-009 |
| D5 | mod-005 | | D6 | mod-010 |
| D1 | mod-006 | | T1 | mod-012 |
| D3 | mod-006 | | T2 | mod-013 |
| D4 | mod-006 | | T3 | mod-014 |
| P1 | mod-006 | | CI1 | mod-015 |
| P2 | mod-006 | | S4 | mod-016 |
|    |         | | DOC1 | mod-017 |
|    |         | | DOC2 | Follow-up (Low severity — not a formal increment) |

### Self-Review Checklist

- [x] Every critical-severity finding has a corresponding increment (A1→001, S1/S2→002, D2→005, D1→006, T1→012)
- [x] No increment combines unrelated changes
- [x] Every increment has a rollback plan
- [x] Dependency ordering is valid (no cycles, no missing refs)
- [x] The first increment (mod-001) is the smallest valuable modernization
- [x] Each increment leaves the application in a deployable, working state
- [x] Acceptance criteria are specific and testable
- [x] No "big bang" increments (mod-006 is largest but touches only namespace/adapters — a mechanical change)
- [x] Every increment includes behavioral deltas
- [x] Regression scope is identified for each increment
