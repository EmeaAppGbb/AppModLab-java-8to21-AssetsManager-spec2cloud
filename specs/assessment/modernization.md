# Modernization Assessment

## Summary

- **Assessment depth**: Level 3 (Deep Assessment)
- **Total findings**: 28
- **Critical**: 5 | **High**: 12 | **Medium**: 9 | **Low**: 2
- **Escalation triggered**: Yes — Level 1 found 17 critical/high items → Level 2; Level 2 found architectural concerns (duplicated models, no shared module, tight coupling) → Level 3

### Project Snapshot

| Attribute | Current | Target |
|-----------|---------|--------|
| Java | 8 (public updates ended Jan 2019) | 21 LTS (supported until Sep 2031) |
| Spring Boot | 2.7.18 (OSS EOL: Jun 2023) | 3.5.x (latest: 3.5.12) |
| AWS SDK | 2.25.13 | 2.42.x (latest: 2.42.21) |
| Jakarta EE | javax.* namespace | jakarta.* namespace (required by Spring Boot 3) |

---

## Findings by Category

### Dependencies

| # | Severity | Finding | Location | Remediation | Effort |
|---|----------|---------|----------|-------------|--------|
| D1 | **Critical** | **Spring Boot 2.7.18 is past OSS end-of-life** (Jun 2023). No community security patches. Latest is 3.5.12. | `pom.xml:9` (parent) | Upgrade to Spring Boot 3.4.x+ (requires Java 17+, jakarta namespace). Use Spring Boot Migrator tool. | 1–2 weeks |
| D2 | **Critical** | **Java 8 target** — public updates ended Jan 2019. Project name indicates Java 21 goal. Java 21 is current LTS (supported until Sep 2031). | `pom.xml:19` (`<java.version>8</java.version>`) | Upgrade to Java 21. Fix source incompatibilities, test with JDK 21. | 1–2 days (language only) |
| D3 | **High** | **javax.persistence → jakarta.persistence** — Spring Boot 3.x requires Jakarta EE 9+ namespace. All JPA annotations use old `javax.persistence`. | `web/../model/ImageMetadata.java`, `worker/../model/ImageMetadata.java` | Find-and-replace `javax.persistence` → `jakarta.persistence`. Update dependencies. | Hours |
| D4 | **High** | **javax.servlet → jakarta.servlet** — `HttpServletRequest`/`HttpServletResponse` must move to `jakarta.servlet`. | `web/../config/WebMvcConfig.java` | Find-and-replace `javax.servlet` → `jakarta.servlet`. | Hours |
| D5 | **High** | **javax.annotation.PostConstruct → jakarta.annotation.PostConstruct** — deprecated in Java 11, removed in Java 17. | `worker/../service/LocalFileProcessingService.java`, `web/../service/LocalFileStorageService.java` | Replace with `jakarta.annotation.PostConstruct` or Spring `@PostConstruct`. | Hours |
| D6 | **Medium** | **AWS SDK 2.25.13 is 17 minor versions behind** latest (2.42.21). Missing bug fixes, performance improvements, and new features. | `web/pom.xml:13`, `worker/pom.xml:13` | Bump `<aws-sdk.version>` to `2.42.21`. Test S3 operations. | Hours |

### Patterns

| # | Severity | Finding | Location | Remediation | Effort |
|---|----------|---------|----------|-------------|--------|
| P1 | **High** | **WebMvcConfigurerAdapter is deprecated** (since Spring 5.0). Acknowledged with `@SuppressWarnings("deprecation")` but not fixed. | `web/../config/WebMvcConfig.java:226` | Change `extends WebMvcConfigurerAdapter` → `implements WebMvcConfigurer`. | Hours |
| P2 | **High** | **HandlerInterceptorAdapter is deprecated** (since Spring 5.3). Inner class extends this deprecated adapter. | `web/../config/WebMvcConfig.java:258` | Change `extends HandlerInterceptorAdapter` → `implements HandlerInterceptor`. | Hours |
| P3 | **High** | **System.out.printf used for logging** in interceptor instead of SLF4J. Not suitable for production (no log levels, no structured output). | `web/../config/WebMvcConfig.java:266-287` | Replace all `System.out.printf` with SLF4J `log.info()`/`log.warn()`. | Hours |
| P4 | **Medium** | **DTOs should be Java records** (Java 16+). `ImageProcessingMessage` and `S3StorageItem` are immutable data carriers using Lombok `@Data`. | `web/../model/ImageProcessingMessage.java`, `web/../model/S3StorageItem.java` + worker equivalents | Convert to `record` types. Remove Lombok dependency for these classes. | Hours |
| P5 | **Medium** | **If-else chains for operation detection** — could use enhanced switch expressions (Java 14+) or pattern matching (Java 21). | `web/../config/WebMvcConfig.java:290-307` (`determineFileOperation`) | Refactor to switch expression. | Hours |
| P6 | **Medium** | **String concatenation in log.error()** — `"message: " + message.getKey()` bypasses SLF4J lazy evaluation. | `web/../service/BackupMessageProcessor.java:939`, `worker/../service/AbstractFileProcessingService.java:457` | Use parameterized logging: `log.error("message: {}", key, e)` | Minutes |
| P7 | **Low** | **Inconsistent logging approach** — some classes use `@Slf4j` (Lombok), others use manual `LoggerFactory.getLogger()`. | `web/../service/LocalFileStorageService.java` vs `web/../service/BackupMessageProcessor.java` | Standardize on `@Slf4j` Lombok annotation across all classes. | Minutes |

### Architecture

| # | Severity | Finding | Location | Remediation | Effort |
|---|----------|---------|----------|-------------|--------|
| A1 | **Critical** | **Missing `@Bean` annotation on `s3Client()` in web module** — S3Client is never registered in the Spring context. `AwsS3Service` injects S3Client but no bean exists. This is a runtime failure. | `web/../config/AwsS3Config.java:114` | Add `@Bean` to the `s3Client()` method. Worker module's config is correct. | Minutes |
| A2 | **High** | **Duplicated model classes across modules** — `ImageMetadata`, `ImageProcessingMessage`, and `ImageMetadataRepository` are copy-pasted in both `web` and `worker`. Drift risk is high. | `web/../model/` and `worker/../model/` | Create a shared `common` Maven module. Move shared models, DTOs, and repository interfaces there. | Days |
| A3 | **High** | **O(n²) performance in `listObjects()`** — for each S3 object, calls `imageMetadataRepository.findAll()` then filters with `.stream().filter()`. For 100 objects = 100 full table scans. | `web/../service/AwsS3Service.java:756-760` | Pre-fetch all metadata once with `findAll()`, build a `Map<String, ImageMetadata>`, then look up by key in O(1). Or add `findByS3Key()` to the repository. | Hours |
| A4 | **High** | **findAll() + filter in uploadThumbnail()** — loads entire `ImageMetadata` table to find one record by S3 key. Same pattern repeated in `deleteObject()`. | `worker/../service/S3FileProcessingService.java:852`, `web/../service/AwsS3Service.java:838` | Add `Optional<ImageMetadata> findByS3Key(String s3Key)` to `ImageMetadataRepository`. | Hours |
| A5 | **Medium** | **Inconsistent AWS property names** — web uses `aws.accessKey` while worker uses `aws.accessKeyId`. This causes confusion and potential config errors. | `web/application.properties:4` vs `worker/application.properties:1` | Standardize on `aws.accessKeyId` (matches AWS convention). | Minutes |
| A6 | **Medium** | **No transaction management for multi-step operations** — `uploadObject()` uploads to S3 then saves to DB without `@Transactional`. Partial failure leaves inconsistent state. | `web/../service/AwsS3Service.java:775-803` | Add `@Transactional` and implement compensating actions (delete S3 object on DB failure). | Hours |

### Security

| # | Severity | Finding | Location | Remediation | Effort |
|---|----------|---------|----------|-------------|--------|
| S1 | **Critical** | **Hardcoded AWS credentials in properties** — `aws.accessKey=your-access-key` and `aws.secretKey=your-secret-key` stored in plain text. Committed to source control. | `web/application.properties:4-5`, `worker/application.properties:1-2` | Use environment variables, AWS IAM roles, or `DefaultCredentialsProvider`. Remove credentials from properties files entirely. | Hours |
| S2 | **Critical** | **Hardcoded database and RabbitMQ credentials** — `postgres:postgres` and `guest:guest` in properties files. | `web/application.properties` (lines 14-17, 20-22), `worker/application.properties` | Externalize all credentials via environment variables: `${DB_PASSWORD}`, `${RABBITMQ_PASSWORD}`. Use Spring Cloud Config or Azure Key Vault. | Hours |
| S3 | **High** | **No input validation on `@PathVariable` keys** — `S3Controller` accepts arbitrary strings as S3 keys. Potential for path traversal or injection attacks. | `web/../controller/S3Controller.java:462,483,500` | Add input validation: reject keys containing `..`, `/`, or other dangerous characters. Use allowlist pattern. | Hours |
| S4 | **Medium** | **`ddl-auto=update` in production config** — Hibernate auto-DDL can cause schema corruption in production. Should only be used in development. | `web/application.properties`, `worker/application.properties` | Use `ddl-auto=validate` (or `none`) for production. Use Flyway or Liquibase for schema migrations. | Days |

### Testing

| # | Severity | Finding | Location | Remediation | Effort |
|---|----------|---------|----------|-------------|--------|
| T1 | **Critical** | **Web module has ZERO tests** — no unit tests, no integration tests, no controller tests. The `web/src/test/` directory exists but is empty. | `web/src/test/` | Add unit tests for all services (AwsS3Service, LocalFileStorageService), controller tests for S3Controller, and integration tests. | 1–2 weeks |
| T2 | **High** | **Worker module has only 4 basic unit tests** — covers only `S3FileProcessingService` with weak assertions. No tests for `AbstractFileProcessingService` (the most complex class), `LocalFileProcessingService`, or error scenarios. | `worker/../service/S3FileProcessingServiceTest.java` | Add tests for thumbnail generation, error handling, message acknowledgment, and local file processing. | 1 week |
| T3 | **High** | **No integration tests** — no tests verify that web↔RabbitMQ↔worker pipeline works end-to-end. No Spring `@SpringBootTest` tests exist. | Entire project | Add `@SpringBootTest` integration tests with Testcontainers (PostgreSQL, RabbitMQ) and S3 mock (LocalStack). | 1–2 weeks |

### DevOps/CI

| # | Severity | Finding | Location | Remediation | Effort |
|---|----------|---------|----------|-------------|--------|
| CI1 | **High** | **No CI/CD pipeline** — no GitHub Actions, no Azure Pipelines, no Jenkins. No automated build verification on commits. | `.github/` (no workflow files) | Create GitHub Actions workflow: build → test → lint for both modules. | Days |
| CI2 | **Medium** | **`show-sql=true` in config** — Hibernate SQL logging is enabled. Degrades performance and creates noisy logs in production. | `web/application.properties` (last line) | Set `spring.jpa.show-sql=false`. Use `logging.level.org.hibernate.SQL=DEBUG` only when needed. | Minutes |

### Documentation

| # | Severity | Finding | Location | Remediation | Effort |
|---|----------|---------|----------|-------------|--------|
| DOC1 | **Medium** | **No API documentation** — no Swagger/OpenAPI, no README describing endpoints. The web app serves HTML but also has REST-like endpoints (`/storage/view/{key}`) without documentation. | Project root | Add Springdoc OpenAPI dependency for auto-generated docs, or create manual API docs. | Hours |
| DOC2 | **Low** | **Inconsistent Javadoc coverage** — `StorageService` interface has Javadoc, but implementations and controllers do not. | Various | Add Javadoc to all public classes and methods. Enforce with checkstyle. | Hours |

---

## Modernization Roadmap

Based on dependency analysis, the following sequencing is recommended. Items are ordered by dependency chain — earlier items unblock later ones.

### Phase 1: Foundation (Must Do First)

```
┌─────────────────────────────────────────────────┐
│ 1. Fix Critical Bug: @Bean on web S3Client (A1) │
│ 2. Externalize all credentials (S1, S2)         │
│ 3. Create shared module for models (A2)         │
└──────────────────────┬──────────────────────────┘
                       │ unblocks
                       ▼
┌─────────────────────────────────────────────────┐
│ 4. Java 8 → 21 upgrade (D2)                    │
│    • Update pom.xml java.version                │
│    • Fix javax.annotation removal               │
│    • Adopt records, switch expressions, etc.    │
└──────────────────────┬──────────────────────────┘
                       │ unblocks
                       ▼
┌─────────────────────────────────────────────────┐
│ 5. Spring Boot 2.7 → 3.x upgrade (D1)          │
│    • javax → jakarta namespace (D3, D4, D5)     │
│    • Fix deprecated adapters (P1, P2)           │
│    • Update Spring properties                   │
└─────────────────────────────────────────────────┘
```

### Phase 2: Quality & Performance

```
┌─────────────────────────────────────────────────┐
│ 6. Fix O(n²) queries (A3, A4)                   │
│ 7. Add input validation (S3)                    │
│ 8. Fix logging (P3, P6, P7)                     │
│ 9. Add transaction management (A6)              │
│ 10. Standardize config properties (A5, CI2)     │
└─────────────────────────────────────────────────┘
```

### Phase 3: Test Coverage & CI

```
┌─────────────────────────────────────────────────┐
│ 11. Add unit tests for web module (T1)          │
│ 12. Expand worker tests (T2)                    │
│ 13. Add integration tests w/ Testcontainers (T3)│
│ 14. Create CI/CD pipeline (CI1)                 │
└─────────────────────────────────────────────────┘
```

### Phase 4: Polish

```
┌─────────────────────────────────────────────────┐
│ 15. Convert DTOs to records (P4)                │
│ 16. Adopt switch expressions (P5)               │
│ 17. Add API documentation (DOC1)                │
│ 18. Schema migration tooling — Flyway (S4)      │
│ 19. Update AWS SDK to latest (D6)               │
│ 20. Javadoc coverage (DOC2)                     │
└─────────────────────────────────────────────────┘
```

---

## Level 3: Deep Assessment — Architecture & Scalability

### Architectural Debt

| Concern | Severity | Analysis |
|---------|----------|----------|
| **Monolithic coupling via shared DB** | High | Both `web` and `worker` modules share the same PostgreSQL database and same `ImageMetadata` table. No API boundary between them — they couple at the database level. If either module's schema needs to diverge, the other breaks. |
| **Duplicated domain model** | High | `ImageMetadata`, `ImageProcessingMessage`, and `ImageMetadataRepository` are copy-pasted between modules. Identical classes, different packages. A change in one module must be manually replicated in the other. |
| **No contract for message queue** | Medium | `ImageProcessingMessage` defines the RabbitMQ contract but is duplicated, not shared. If web adds a field and worker doesn't, deserialization breaks silently (Jackson ignores unknown fields by default). |
| **Single storage abstraction, dual identity** | Medium | The `StorageService`/`FileProcessor` strategy pattern is good, but the two interfaces (`StorageService` in web, `FileProcessor` in worker) have different shapes. A unified storage abstraction would reduce duplication. |

### Performance Anti-Patterns

| Pattern | Impact | Details |
|---------|--------|---------|
| **N+1 metadata lookups** | High | `AwsS3Service.listObjects()` makes 1 call to S3, then N calls to DB (via `findAll()` in a loop). This is O(n²) in the number of objects. |
| **Full table scan for single record** | High | `findAll().stream().filter()` pattern appears 3 times across the codebase. Should be `findByS3Key()` JPA query method. |
| **Synchronous thumbnail generation** | Medium | RabbitMQ provides async decoupling, which is good. However, the thumbnail generation itself is CPU-bound with no thread pool sizing or backpressure. Under load, the worker could OOM from BufferedImage allocations. |
| **Client-side polling for thumbnail status** | Low | The UI polls every 3-6 seconds to detect new thumbnails. WebSocket or SSE would be more efficient and responsive. |

### Scalability Limits

| Limit | Impact | Notes |
|-------|--------|-------|
| **Shared PostgreSQL** | Medium | Both services must use the same DB instance. Cannot independently scale DB for read-heavy (web) vs write-heavy (worker) workloads. |
| **Single RabbitMQ queue** | Low | Both services declare the same queue. Currently works, but limits independent deployment. |
| **No pagination on S3 listing** | Medium | `listObjectsV2` returns up to 1000 objects by default. For large buckets, pagination is needed. UI also renders all objects at once. |

### Migration Path Summary

The migration from Java 8 + Spring Boot 2.7 to Java 21 + Spring Boot 3.x is a **well-understood, incremental path** supported by official tooling:

1. **Spring Boot Migrator** (`spring-boot-migrator`) can automate many javax→jakarta changes.
2. **OpenRewrite** recipes exist for both Java version upgrades and Spring Boot migration.
3. The codebase is small (~1200 lines of Java across both modules) making manual migration feasible.
4. No use of removed Java APIs (Nashorn, security manager, etc.) that would block the upgrade.
5. The AWS SDK v2 is already used (not v1), so no SDK migration needed.

### Dependency Risk Matrix

| Dependency | Current | Latest | Risk Level | Notes |
|------------|---------|--------|------------|-------|
| Spring Boot | 2.7.18 | 3.5.12 | **High** | EOL, no security patches |
| Java | 8 | 21 | **High** | Required for Spring Boot 3 |
| AWS SDK v2 | 2.25.13 | 2.42.21 | Low | API stable, drop-in update |
| PostgreSQL JDBC | managed | managed | Low | Spring Boot manages version |
| Lombok | managed | managed | Low | Stable, widely used |
| Jackson | managed | managed | Low | Spring Boot manages version |
| Spring AMQP | managed | managed | Low | Comes with Spring Boot |

---

## Decision Points

The following items require user decisions and have corresponding ADRs:

| Decision | ADR | Options | Recommendation |
|----------|-----|---------|----------------|
| **Java 8 → 21 upgrade** | ADR-001 | Java 17 (min for SB3) vs Java 21 (latest LTS) | Java 21 — latest LTS, supported until 2031 |
| **Spring Boot 2.7 → 3.x** | ADR-002 | SB 3.4.x (proven) vs SB 3.5.x (latest) | SB 3.4.x — stable, well-documented migration path |
| **Shared module for duplicated code** | ADR-003 | Shared Maven module vs keep duplicated | Shared module — eliminates drift risk |
