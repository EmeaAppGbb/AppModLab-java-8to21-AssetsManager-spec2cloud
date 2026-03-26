# FRD: Dual Storage Backend

**Feature ID**: F-007
**Status**: Draft
**Priority**: P1 (High)
**Last Updated**: 2026-03-26

## Description

The Dual Storage Backend provides a strategy pattern abstraction that allows the application to switch between AWS S3 (production) and local filesystem (development) storage with zero code changes. The switching is controlled by Spring profiles: `@Profile("dev")` activates local storage, and `@Profile("!dev")` activates S3 storage. Both the web module and worker module implement this pattern independently through parallel interface hierarchies (`StorageService` in web, `FileProcessor` in worker). The storage type is embedded in RabbitMQ messages so the worker only processes messages matching its active storage backend.

## User Stories

### US-F007-001: Develop Without AWS

**As a** System Operator
**I want to** run the application locally without AWS credentials
**So that** I can develop and test without cloud infrastructure costs

**Acceptance Criteria:**
- GIVEN the `dev` Spring profile is active WHEN I upload a file THEN it is stored in the local filesystem at `../storage/`
- GIVEN the `dev` profile is active WHEN I list files THEN files from the local directory are displayed

### US-F007-002: Deploy to Production with S3

**As a** System Operator
**I want to** use AWS S3 in production
**So that** files are stored durably and scalably in cloud storage

**Acceptance Criteria:**
- GIVEN no `dev` profile is active WHEN I upload a file THEN it is stored in the configured S3 bucket
- GIVEN the S3 profile is active WHEN files are listed THEN objects from the S3 bucket are returned

## Functional Requirements

### FR-F007-001: Profile-Based Service Selection

- Input: Spring active profile configuration
- Processing: Spring's `@Profile` annotation selects the active implementation. Web module: `LocalFileStorageService` (dev) or `AwsS3Service` (!dev). Worker module: `LocalFileProcessingService` (dev) or `S3FileProcessingService` (!dev).
- Output: Single active `StorageService`/`FileProcessor` bean per module
- Error handling: If required profile properties are missing (e.g., AWS credentials), application fails to start.

### FR-F007-002: Storage Type in Messages

- Input: Upload event
- Processing: `StorageService.getStorageType()` returns "s3" or "local". This value is embedded in the `ImageProcessingMessage.storageType` field.
- Output: RabbitMQ message tagged with storage type
- Error handling: Worker checks `message.getStorageType().equals(getStorageType())` and skips non-matching messages (ACK without processing).

### FR-F007-003: Local Storage Directory Management

- Input: Application startup in dev profile
- Processing: `@PostConstruct` method resolves `${local.storage.directory:../storage}`, normalizes the path, and creates the directory if it doesn't exist.
- Output: Storage directory ready for file operations
- Error handling: `IOException` on directory creation propagates and prevents startup.

## Dependencies

| Dependency | Type | Direction | Description |
|------------|------|-----------|-------------|
| AWS S3 | External | — | Production file storage |
| Local filesystem | External | — | Development file storage |
| Spring Profiles | Infrastructure | — | Service selection mechanism |

---

## Current Implementation (Brownfield Extension)

### Files Involved

| File Path | Role | Lines |
|-----------|------|-------|
| `web/src/main/java/.../service/StorageService.java` | Web storage interface | 1-40 |
| `web/src/main/java/.../service/AwsS3Service.java` | S3 web implementation | 1-135 |
| `web/src/main/java/.../service/LocalFileStorageService.java` | Local web implementation | 1-140 |
| `web/src/main/java/.../config/AwsS3Config.java` | S3 client config (web) | 1-30 |
| `worker/src/main/java/.../service/FileProcessor.java` | Worker storage interface | 1-8 |
| `worker/src/main/java/.../service/S3FileProcessingService.java` | S3 worker implementation | 1-90 |
| `worker/src/main/java/.../service/LocalFileProcessingService.java` | Local worker implementation | 1-70 |
| `worker/src/main/java/.../config/AwsS3Config.java` | S3 client config (worker) | 1-30 |

### Architecture Pattern

Strategy pattern with Spring profile-based selection. Two parallel hierarchies: web (`StorageService` → `AwsS3Service` / `LocalFileStorageService`) and worker (`FileProcessor` → `S3FileProcessingService` / `LocalFileProcessingService`). The interfaces have different shapes — they are not shared across modules.

### Test Coverage

| Test Type | Files | Assertions | Coverage |
|-----------|-------|------------|----------|
| Unit | `S3FileProcessingServiceTest.java` | 2 (storage type + download) | ~10% |

### Known Limitations

- Web and worker have independent interface hierarchies — no shared storage abstraction (assessment finding A2)
- Web `AwsS3Config.s3Client()` missing `@Bean` annotation (assessment finding A1)
- Inconsistent AWS property names: web uses `aws.accessKey`, worker uses `aws.accessKeyId` (assessment finding A5)
- `LocalFileStorageService` does not persist metadata to database — metadata features only work in S3 mode
