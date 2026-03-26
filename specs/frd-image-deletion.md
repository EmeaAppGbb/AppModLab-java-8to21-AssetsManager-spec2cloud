# FRD: Image Deletion

**Feature ID**: F-005
**Status**: Draft
**Priority**: P1 (High)
**Last Updated**: 2026-03-26

## Description

Image Deletion removes an image and its associated thumbnail from the storage backend and deletes the corresponding metadata from the PostgreSQL database. Deletion is initiated from either the gallery card or the detail view page via a POST form submission. A JavaScript confirmation dialog prevents accidental deletions. After deletion, the user is redirected to the gallery with a success or error flash message.

## User Stories

### US-F005-001: Delete an Image

**As a** Content Manager
**I want to** delete an unwanted image
**So that** it no longer appears in my gallery and storage is freed

**Acceptance Criteria:**
- GIVEN I click Delete on a gallery card or detail page WHEN the confirmation dialog appears and I confirm THEN the image and its thumbnail are removed from storage, metadata is deleted from the database, and I am redirected to the gallery with a success message
- GIVEN I click Delete WHEN the confirmation dialog appears and I cancel THEN nothing is deleted and I stay on the current page
- GIVEN the deletion fails WHEN an error occurs THEN I am redirected to the gallery with an error message

## Functional Requirements

### FR-F005-001: Delete Image from Storage

- Input: HTTP POST to `/storage/delete/{key}`
- Processing: Call `StorageService.deleteObject(key)`. In S3 mode: delete the original object, then attempt to delete the thumbnail (key with `_thumbnail` suffix) — silently ignore if thumbnail doesn't exist. In local mode: delete the file, then attempt to delete the thumbnail file — log warning if thumbnail not found.
- Output: Redirect to `/storage` with flash attribute `success` = "File deleted successfully"
- Error handling: Generic `Exception` caught → redirect to `/storage` with flash attribute `error`

### FR-F005-002: Delete Metadata from Database (Production)

- Input: Successful storage deletion in S3 mode
- Processing: In `AwsS3Service.deleteObject()`, find metadata record by streaming `findAll()` and filtering by `s3Key`, then delete the found record via `imageMetadataRepository.delete()`.
- Output: Row removed from `image_metadata` table
- Error handling: No explicit error handling — metadata may become orphaned if deletion fails

### FR-F005-003: Client-Side Deletion Confirmation

- Input: User clicks Delete button
- Processing: JavaScript `confirm()` dialog with message "Are you sure you want to delete this file?"
- Output: If confirmed, form submits. If cancelled, no action.

## Dependencies

| Dependency | Type | Direction | Description |
|------------|------|-----------|-------------|
| F-001 Image Upload | Feature | Upstream | Deletes images that were uploaded |
| F-007 Dual Storage Backend | Feature | Upstream | `deleteObject()` from storage |
| F-008 Image Metadata | Feature | Upstream | Metadata deletion |
| AWS S3 | External | — | Object deletion in production |
| PostgreSQL | External | — | Metadata deletion |

---

## Current Implementation (Brownfield Extension)

### Files Involved

| File Path | Role | Lines |
|-----------|------|-------|
| `web/src/main/java/.../controller/S3Controller.java` | Delete endpoint | 96-105 |
| `web/src/main/java/.../service/AwsS3Service.java` | S3 delete + metadata cleanup | 110-135 |
| `web/src/main/java/.../service/LocalFileStorageService.java` | Local file delete | 117-137 |

### Test Coverage

| Test Type | Files | Assertions | Coverage |
|-----------|-------|------------|----------|
| Unit | — | 0 | 0% |

### Known Limitations

- Metadata lookup uses `findAll().stream().filter()` — loads entire table for one record (assessment finding A4)
- Thumbnail deletion silently catches all exceptions in S3 mode (empty catch block)
- No input validation on `key` path variable — path traversal risk (assessment finding S3)
- Deletion of storage object and metadata is not transactional — partial failure can leave orphaned data
