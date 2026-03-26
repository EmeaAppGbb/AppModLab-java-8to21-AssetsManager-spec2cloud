# FRD: Async Thumbnail Generation

**Feature ID**: F-006
**Status**: Draft
**Priority**: P0 (Critical)
**Last Updated**: 2026-03-26

## Description

Async Thumbnail Generation is the background processing pipeline that creates optimized thumbnail images from uploaded originals. The system uses a dedicated Spring Boot worker application that consumes messages from a RabbitMQ `image-processing` queue. For each message, the worker downloads the original image from storage, generates a high-quality thumbnail (max 600px on the longest dimension) using progressive multi-step scaling with bicubic interpolation and a sharpening filter, then uploads the thumbnail back to storage with a `_thumbnail` suffix in the key. In production mode, the worker also updates the image metadata record in PostgreSQL with the thumbnail key and URL.

The worker uses manual RabbitMQ acknowledgment: messages are ACK'd on success and NACK'd (without requeue) on failure, routing failed messages to a dead-letter exchange if configured. Temporary files used during processing are always cleaned up in a `finally` block.

## User Stories

### US-F006-001: Automatic Thumbnail Generation

**As a** Content Manager
**I want to** have thumbnails generated automatically after I upload an image
**So that** the gallery can display optimized previews without me doing extra work

**Acceptance Criteria:**
- GIVEN I upload an image WHEN the worker processes the message THEN a thumbnail appears in storage with key `{originalKey}_thumbnail.{ext}` and the gallery shows the thumbnail
- GIVEN I upload a large image (e.g., 4000×3000) WHEN the thumbnail is generated THEN the longest dimension is 600px with aspect ratio preserved
- GIVEN I upload a JPEG image WHEN the thumbnail is generated THEN it uses 95% compression quality
- GIVEN I upload a PNG image WHEN the thumbnail is generated THEN it uses lossless compression

### US-F006-002: Reliable Message Processing

**As a** System Operator
**I want to** messages to be reliably processed with proper error handling
**So that** failed thumbnails don't block the queue and successful ones are not reprocessed

**Acceptance Criteria:**
- GIVEN a message is processed successfully WHEN the worker finishes THEN the message is acknowledged and removed from the queue
- GIVEN processing fails WHEN an exception occurs THEN the message is NACK'd without requeue (routed to DLX) and the failure is logged

## Functional Requirements

### FR-F006-001: Message Consumption

- Input: JSON message from `image-processing` queue: `{key, contentType, storageType, size}`
- Processing: Deserialize via `Jackson2JsonMessageConverter`. Check if `storageType` matches this worker's type. If not, ACK and skip (not an error).
- Output: Trigger download → thumbnail → upload pipeline
- Error handling: All exceptions caught in outer try/catch. `processingSuccess` flag controls ACK/NACK in `finally` block.

### FR-F006-002: Thumbnail Generation Algorithm

- Input: Original image file (downloaded to temp directory)
- Processing:
  1. Read image via `ImageIO.read()`
  2. Calculate target dimensions: max 600px on longest side, aspect ratio preserved
  3. Progressive scaling: reduce by max 50% per step until within 1.5× of target, then final scale to exact dimensions
  4. Rendering hints: BICUBIC interpolation, QUALITY rendering, ANTIALIAS on, QUALITY color rendering, DITHER enabled
  5. Sharpening: 3×3 convolution kernel `[0, -0.2, 0, -0.2, 1.8, -0.2, 0, -0.2, 0]`
  6. Format-specific output: JPEG at 0.95 quality; PNG with Deflate at best quality; other formats via standard `ImageIO.write()`
  7. Transparency preserved via `TYPE_INT_ARGB` for non-opaque images
- Output: Thumbnail file in temp directory
- Error handling: `IOException` if image cannot be read

### FR-F006-003: Metadata Update (Production)

- Input: Successful thumbnail upload in S3 mode
- Processing: Extract original key from thumbnail key (remove `_thumbnail` suffix). Find metadata record via `findAll().stream().filter(s3Key == originalKey)`. Update `thumbnailKey` and `thumbnailUrl` fields. Save via repository.
- Output: Updated `image_metadata` row with thumbnail references
- Error handling: If no matching metadata found, no update occurs (silent skip via `ifPresent`).

### FR-F006-004: Temporary File Cleanup

- Input: Processing completion (success or failure)
- Processing: In `finally` block, delete original temp file, thumbnail temp file, and temp directory using `Files.deleteIfExists()`.
- Output: No temp files remain on disk after processing
- Error handling: `IOException` caught and logged during cleanup.

## Dependencies

| Dependency | Type | Direction | Description |
|------------|------|-----------|-------------|
| F-001 Image Upload | Feature | Upstream | Upload dispatches processing message |
| F-007 Dual Storage Backend | Feature | Upstream | Worker downloads/uploads via storage |
| F-008 Image Metadata | Feature | Downstream | Worker updates thumbnail metadata |
| RabbitMQ | External | — | Message broker |
| AWS S3 | External | — | Storage (production) |
| PostgreSQL | External | — | Metadata update (production) |

---

## Current Implementation (Brownfield Extension)

### Files Involved

| File Path | Role | Lines |
|-----------|------|-------|
| `worker/src/main/java/.../service/AbstractFileProcessingService.java` | Message consumer + thumbnail algorithm | 1-277 |
| `worker/src/main/java/.../service/S3FileProcessingService.java` | S3 storage operations + metadata update | 1-90 |
| `worker/src/main/java/.../service/LocalFileProcessingService.java` | Local storage operations | 1-70 |
| `worker/src/main/java/.../service/FileProcessor.java` | Strategy interface | 1-8 |
| `worker/src/main/java/.../config/RabbitConfig.java` | Queue + converter config | 1-40 |
| `worker/src/main/java/.../util/StorageUtil.java` | Key manipulation utilities | 1-20 |

### Architecture Pattern

Template Method pattern: `AbstractFileProcessingService` defines the processing pipeline (`processImage`), subclasses (`S3FileProcessingService`, `LocalFileProcessingService`) implement storage-specific operations (`downloadOriginal`, `uploadThumbnail`, `generateUrl`). Strategy pattern via `FileProcessor` interface.

### Test Coverage

| Test Type | Files | Assertions | Coverage |
|-----------|-------|------------|----------|
| Unit | `S3FileProcessingServiceTest.java` | 4 | ~15% |

**Untested paths**: Thumbnail generation algorithm, progressive scaling, sharpening filter, JPEG/PNG quality settings, message acknowledgment logic, error handling, temporary file cleanup, local file processing, storage type mismatch handling.

### Known Limitations

- `findAll().stream().filter()` to update metadata — loads entire table (assessment finding A4)
- Hardcoded thumbnail max dimension (600px) — should be configurable
- No timeout on image processing — very large images could consume excessive memory/CPU
- No thread pool sizing or backpressure for concurrent processing
- Generic `Exception` catch is too broad — masks specific failure causes
- String concatenation in `log.error()` (assessment finding P6)
- Resource leak risk in `ImageWriter` disposal paths during JPEG/PNG output

### Integration Points

| External System | Protocol | Purpose | Config Location |
|----------------|----------|---------|-----------------|
| RabbitMQ | AMQP | Message consumption | `worker/application.properties` |
| AWS S3 | HTTPS | Download originals, upload thumbnails | `worker/application.properties` |
| PostgreSQL | JDBC | Metadata update | `worker/application.properties` |
| Local filesystem | File I/O | Dev mode storage + temp files | `local.storage.directory` property |
