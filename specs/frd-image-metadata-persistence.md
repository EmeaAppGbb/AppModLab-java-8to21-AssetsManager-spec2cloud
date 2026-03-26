# FRD: Image Metadata Persistence

**Feature ID**: F-008
**Status**: Draft
**Priority**: P1 (High)
**Last Updated**: 2026-03-26

## Description

Image Metadata Persistence stores structured information about uploaded images in a PostgreSQL database via JPA/Hibernate. The `ImageMetadata` entity records the original filename, content type, file size, S3 storage key, S3 URL, thumbnail key, thumbnail URL, and timestamps for upload and last modification. The entity uses `@PrePersist` and `@PreUpdate` lifecycle callbacks for automatic timestamp management. The repository provides standard CRUD via Spring Data `JpaRepository`. Metadata is created during upload (web module, production profile only) and updated with thumbnail references after worker processing.

## User Stories

### US-F008-001: Track Image Metadata

**As a** Content Manager
**I want to** have upload timestamps and file details persisted
**So that** I can see when images were uploaded and their original properties

**Acceptance Criteria:**
- GIVEN I upload an image in production mode WHEN the upload succeeds THEN a metadata record is created with filename, content type, size, S3 key, S3 URL, and upload timestamp
- GIVEN the worker generates a thumbnail WHEN processing completes THEN the metadata record is updated with thumbnail key and thumbnail URL

## Functional Requirements

### FR-F008-001: Metadata Entity Schema

- Fields: `id` (String, PK), `filename`, `contentType`, `size` (Long), `s3Key`, `s3Url`, `thumbnailKey`, `thumbnailUrl`, `uploadedAt` (LocalDateTime), `lastModified` (LocalDateTime)
- `@PrePersist`: sets `uploadedAt` and `lastModified` to `LocalDateTime.now()`
- `@PreUpdate`: sets `lastModified` to `LocalDateTime.now()`

### FR-F008-002: Repository Operations

- Interface: `JpaRepository<ImageMetadata, String>`
- Provided operations: `save()`, `findAll()`, `findById()`, `delete()`
- No custom query methods defined

## Dependencies

| Dependency | Type | Direction | Description |
|------------|------|-----------|-------------|
| PostgreSQL | External | — | Relational database |
| F-001 Image Upload | Feature | Upstream | Creates metadata records |
| F-006 Async Thumbnail | Feature | Upstream | Updates thumbnail fields |
| F-005 Image Deletion | Feature | Upstream | Deletes metadata records |

---

## Current Implementation (Brownfield Extension)

### Files Involved

| File Path | Role | Lines |
|-----------|------|-------|
| `web/src/main/java/.../model/ImageMetadata.java` | JPA entity (web copy) | 1-35 |
| `worker/src/main/java/.../model/ImageMetadata.java` | JPA entity (worker copy) | 1-35 |
| `web/src/main/java/.../repository/ImageMetadataRepository.java` | Repository (web copy) | 1-10 |
| `worker/src/main/java/.../repository/ImageMetadataRepository.java` | Repository (worker copy) | 1-10 |

### Architecture Pattern

Spring Data JPA repository pattern. Entity is duplicated across web and worker modules (assessment finding A2).

### Test Coverage

| Test Type | Files | Assertions | Coverage |
|-----------|-------|------------|----------|
| Unit | — | 0 | 0% |

### Known Limitations

- Entity and repository are duplicated across web and worker modules — drift risk (assessment finding A2)
- String-based primary key (`id`) set manually via UUID — no `@GeneratedValue`
- No `findByS3Key()` method — all lookups use `findAll().stream().filter()` (assessment findings A3, A4)
- No database indexes defined beyond the primary key
- Uses `javax.persistence` namespace — must migrate to `jakarta.persistence` for Spring Boot 3 (assessment finding D3)
- `ddl-auto=update` manages schema — no migration tooling (assessment finding S4)
- Metadata only persisted in production (S3) profile — local dev mode has no metadata
