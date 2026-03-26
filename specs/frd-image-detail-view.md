# FRD: Image Detail View

**Feature ID**: F-003
**Status**: Draft
**Priority**: P1 (High)
**Last Updated**: 2026-03-26

## Description

The Image Detail View provides a full-screen view of a single uploaded image with its metadata. Users navigate here from the gallery by clicking a card's "View" button. The page displays the image at full resolution (max-height 70vh), breadcrumb navigation, and metadata including file size, upload date, and last modified date. Actions available are: download the original file and delete the image (with confirmation).

## User Stories

### US-F003-001: View Full-Size Image

**As a** Content Manager
**I want to** view an image at full resolution with its metadata
**So that** I can inspect the image quality and details

**Acceptance Criteria:**
- GIVEN I click View on a gallery card WHEN the detail page loads THEN I see the full-size image, file size in KB, upload date, and last modified date
- GIVEN the image key does not exist WHEN I navigate to the view page THEN I am redirected to the gallery with an error message "Image not found"

## Functional Requirements

### FR-F003-001: Render Image Detail Page

- Input: HTTP GET to `/storage/view-page/{key}`
- Processing: Call `StorageService.listObjects()`, filter by key using `stream().filter().findFirst()`. If found, pass the `S3StorageItem` to the `view.html` template. If not found, redirect with error flash attribute.
- Output: Thymeleaf `view.html` template with `object` model attribute
- Error handling: Object not found → redirect to `/storage` with "Image not found". Generic exception → redirect with "Failed to view image: {message}".

## Non-Functional Requirements

### NFR-F003-001: Image Display

Image is rendered with `max-height: 70vh` and `img-fluid` class for responsive sizing.

## Dependencies

| Dependency | Type | Direction | Description |
|------------|------|-----------|-------------|
| F-002 Image Gallery | Feature | Upstream | Navigation from gallery cards |
| F-004 Image Download | Feature | Downstream | Download button on detail page |
| F-005 Image Deletion | Feature | Downstream | Delete button on detail page |
| F-007 Dual Storage Backend | Feature | Upstream | `listObjects()` for finding image |

---

## Current Implementation (Brownfield Extension)

### Files Involved

| File Path | Role | Lines |
|-----------|------|-------|
| `web/src/main/java/.../controller/S3Controller.java` | View-page endpoint | 58-77 |
| `web/src/main/resources/templates/view.html` | Detail page UI | 1-55 |

### Architecture Pattern

MVC. Inefficient: calls `listObjects()` (which fetches ALL objects) then filters client-side to find one item. Should use a direct get-by-key method.

### Test Coverage

| Test Type | Files | Assertions | Coverage |
|-----------|-------|------------|----------|
| Unit | — | 0 | 0% |

**Untested paths**: Found object rendering, not-found redirect, exception handling.

### Known Limitations

- Calls `listObjects()` to find a single object — O(n) scan instead of O(1) lookup
- Content type not detected — download always uses `application/octet-stream`
