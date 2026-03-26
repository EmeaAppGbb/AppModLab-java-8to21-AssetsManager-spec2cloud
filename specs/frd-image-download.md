# FRD: Image Download

**Feature ID**: F-004
**Status**: Draft
**Priority**: P1 (High)
**Last Updated**: 2026-03-26

## Description

Image Download provides a direct streaming endpoint that returns the raw image bytes for a given storage key. The endpoint serves dual purposes: inline display (used by `<img>` tags in the gallery and detail view) and file download (triggered by the download button on the detail page which adds an HTML `download` attribute). The response uses `application/octet-stream` as a generic content type regardless of the actual image format.

## User Stories

### US-F004-001: Download Original Image

**As a** Content Manager
**I want to** download the original image file
**So that** I can use it outside the application

**Acceptance Criteria:**
- GIVEN I am on the image detail page WHEN I click the Download button THEN the browser downloads the original image file
- GIVEN the image key does not exist WHEN requesting the download endpoint THEN I receive a 404 Not Found response

## Functional Requirements

### FR-F004-001: Stream Image Content

- Input: HTTP GET to `/storage/view/{key}`
- Processing: Call `StorageService.getObject(key)` which returns an `InputStream`. In S3 mode, this issues a `GetObjectRequest`. In local mode, this returns a `BufferedInputStream` from the file. Wrap the stream in `InputStreamResource`.
- Output: `ResponseEntity<InputStreamResource>` with `Content-Type: application/octet-stream`
- Error handling: `IOException` → return `ResponseEntity.notFound().build()` (HTTP 404)

## Non-Functional Requirements

### NFR-F004-001: Content Type

Response always uses `application/octet-stream` regardless of actual image MIME type. No content-type detection is implemented.

## Dependencies

| Dependency | Type | Direction | Description |
|------------|------|-----------|-------------|
| F-003 Image Detail View | Feature | Upstream | Download button on detail page |
| F-007 Dual Storage Backend | Feature | Upstream | `getObject()` from storage |

---

## Current Implementation (Brownfield Extension)

### Files Involved

| File Path | Role | Lines |
|-----------|------|-------|
| `web/src/main/java/.../controller/S3Controller.java` | View/download endpoint | 79-94 |
| `web/src/main/java/.../service/AwsS3Service.java` | S3 getObject | 98-108 |
| `web/src/main/java/.../service/LocalFileStorageService.java` | Local getObject | 108-115 |

### Test Coverage

| Test Type | Files | Assertions | Coverage |
|-----------|-------|------------|----------|
| Unit | — | 0 | 0% |

### Known Limitations

- Content type is always `application/octet-stream` — should detect from file extension or stored metadata
- `InputStream` from `getObject()` is not explicitly closed by the caller; relies on Spring framework cleanup
- No input validation on the `key` path variable (assessment finding S3)
