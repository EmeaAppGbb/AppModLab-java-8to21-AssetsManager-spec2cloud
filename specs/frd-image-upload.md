# FRD: Image Upload

**Feature ID**: F-001
**Status**: Draft
**Priority**: P0 (Critical)
**Last Updated**: 2026-03-26

## Description

Image Upload is the primary entry point for content in the Assets Manager application. Users access a dedicated upload page with a drag-and-drop zone and standard file picker to select image files. The system validates that the file is non-empty (server-side) and within the 10MB size limit (Spring multipart resolver). On submission, the file is stored in the configured storage backend (AWS S3 in production, local filesystem in development), a processing message is dispatched to the `image-processing` RabbitMQ queue for asynchronous thumbnail generation, and image metadata is persisted to the PostgreSQL database (production profile only). The user is redirected to the gallery with a success or error flash message.

The upload form provides a rich client-side experience: drag-and-drop with visual feedback, client-side image preview before submission, and `accept="image/*"` filtering on the file input. After upload, `sessionStorage` flags are set to trigger aggressive polling on the gallery page so the user sees their thumbnail appear quickly.

## User Stories

### US-F001-001: Upload Image via Form

**As a** Content Manager
**I want to** upload an image file through the web interface
**So that** it is stored and available for viewing in the gallery

**Acceptance Criteria:**
- GIVEN I am on the upload page WHEN I select a valid image file and click Upload THEN the file is stored in the configured backend and I am redirected to the gallery with a success message
- GIVEN I am on the upload page WHEN I submit the form without selecting a file THEN I see an error message "Please select a file to upload" and remain on the upload page
- GIVEN I select a file larger than 10MB WHEN I click Upload THEN I see an error message indicating the upload failed

### US-F001-002: Upload Image via Drag-and-Drop

**As a** Content Manager
**I want to** drag and drop an image onto the upload zone
**So that** I can upload images quickly without using the file picker

**Acceptance Criteria:**
- GIVEN I am on the upload page WHEN I drag an image file over the drop zone THEN the zone highlights with a blue border and light blue background
- GIVEN I drop an image file on the drop zone WHEN the drop completes THEN the file input is populated and a preview of the image is displayed
- GIVEN I have dropped a file WHEN I click Upload THEN the file is uploaded as normal

### US-F001-003: Preview Image Before Upload

**As a** Content Manager
**I want to** see a preview of my selected image before uploading
**So that** I can confirm I selected the correct file

**Acceptance Criteria:**
- GIVEN I select or drop an image file WHEN the file is loaded THEN a preview thumbnail of the image appears below the form
- GIVEN the preview is showing WHEN I click Upload THEN the displayed file is the one uploaded

## Functional Requirements

### FR-F001-001: File Storage

- Input: `MultipartFile` from HTTP POST multipart form-data
- Processing: In production (`!dev` profile), generate a UUID-prefixed key (`{UUID}-{originalFilename}`), upload to the configured S3 bucket via `PutObjectRequest` with content type preserved. In development (`dev` profile), store to the local directory using `Files.copy()` with the original filename, validating the path with `StringUtils.cleanPath()` and rejecting filenames containing `..`.
- Output: File stored in backend. Redirect to `/storage` with flash attribute `success`.
- Error handling: `IOException` caught → redirect to `/storage/upload` with flash attribute `error` containing the exception message.

### FR-F001-002: Message Dispatch for Thumbnail Generation

- Input: Successful file storage operation
- Processing: Create an `ImageProcessingMessage` with the storage key, content type, storage type ("s3" or "local"), and file size. Send to the `image-processing` RabbitMQ queue via `RabbitTemplate.convertAndSend()`.
- Output: JSON message on the `image-processing` queue
- Error handling: No explicit error handling for message dispatch failure. If RabbitMQ is down, the upload operation will throw an uncaught exception.

### FR-F001-003: Metadata Persistence (Production Only)

- Input: Successful file storage
- Processing: Create an `ImageMetadata` entity with a UUID id, original filename, content type, file size, S3 key, and generated S3 URL. Persist via `ImageMetadataRepository.save()`. Timestamps (`uploadedAt`, `lastModified`) are auto-set by `@PrePersist`.
- Output: Row in `image_metadata` PostgreSQL table
- Error handling: No explicit error handling. JPA exceptions propagate uncaught.

### FR-F001-004: Empty File Validation

- Input: Submitted `MultipartFile`
- Processing: Check `file.isEmpty()`. If true, reject.
- Output: Redirect to `/storage/upload` with error flash attribute "Please select a file to upload"
- Error handling: Graceful redirect with user-friendly message.

## Non-Functional Requirements

### NFR-F001-001: File Size Limit

Maximum upload size is 10MB per file and per request, enforced by Spring's multipart resolver (`spring.servlet.multipart.max-file-size=10MB`, `spring.servlet.multipart.max-request-size=10MB`).

### NFR-F001-002: Client-Side File Type Filtering

The file input uses `accept="image/*"` to restrict the file picker to image MIME types. This is client-side only — no server-side content-type validation exists.

## Dependencies

| Dependency | Type | Direction | Description |
|------------|------|-----------|-------------|
| F-007 Dual Storage Backend | Feature | Upstream | Upload delegates to `StorageService` implementation |
| F-006 Async Thumbnail Generation | Feature | Downstream | Upload triggers thumbnail processing via RabbitMQ |
| F-008 Image Metadata Persistence | Feature | Downstream | Upload creates metadata record in production |
| RabbitMQ | External | — | Message broker for thumbnail queue |
| AWS S3 | External | — | File storage in production profile |
| PostgreSQL | External | — | Metadata storage in production profile |

---

## Current Implementation (Brownfield Extension)

### Files Involved

| File Path | Role | Lines |
|-----------|------|-------|
| `web/src/main/java/.../controller/S3Controller.java` | Upload endpoint (GET + POST) | 36-56 |
| `web/src/main/java/.../service/AwsS3Service.java` | S3 upload implementation | 67-96 |
| `web/src/main/java/.../service/LocalFileStorageService.java` | Local upload implementation | 83-105 |
| `web/src/main/java/.../service/StorageService.java` | Upload interface definition | 24-26 |
| `web/src/main/java/.../model/ImageProcessingMessage.java` | RabbitMQ message DTO | 1-12 |
| `web/src/main/resources/templates/upload.html` | Upload form UI | 1-128 |
| `web/src/main/resources/application.properties` | File size limits | 10-11 |

### Architecture Pattern

MVC pattern: `S3Controller` → `StorageService` (interface) → `AwsS3Service` or `LocalFileStorageService` (profile-switched). Strategy pattern for storage backend selection. RabbitMQ message dispatch happens inside the service layer (not in the controller).

### Test Coverage

| Test Type | Files | Assertions | Coverage |
|-----------|-------|------------|----------|
| Unit | — | 0 | 0% |
| Integration | — | 0 | 0% |
| E2E | — | 0 | 0% |

**Untested paths**: All upload paths — empty file validation, successful upload to S3, successful upload to local, RabbitMQ message dispatch, metadata persistence, IOException handling, path traversal rejection in local mode.

### Known Limitations

- No server-side content-type validation — any file type accepted if `accept` attribute is bypassed
- No `@Transactional` on upload — S3 upload + DB save are not atomic (assessment finding A6)
- RabbitMQ dispatch failure after storage is not compensated — file exists in storage but no thumbnail is generated
- Missing `@Bean` on web `AwsS3Config.s3Client()` — S3 upload will fail at runtime in production profile (assessment finding A1)
- Path traversal prevention only in local mode (`StringUtils.cleanPath`); S3 mode has no key validation

### Integration Points

| External System | Protocol | Purpose | Config Location |
|----------------|----------|---------|-----------------|
| AWS S3 | HTTPS | File storage (production) | `web/application.properties` (aws.*) |
| RabbitMQ | AMQP | Thumbnail generation dispatch | `web/application.properties` (spring.rabbitmq.*) |
| PostgreSQL | JDBC | Metadata persistence | `web/application.properties` (spring.datasource.*) |
| Local filesystem | File I/O | File storage (development) | `local.storage.directory` property |
