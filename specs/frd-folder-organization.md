# FRD: Folder/Album Organization

**Feature ID**: F-011
**Status**: Draft
**Priority**: P1 (High)
**Last Updated**: 2026-03-27

## Description

Folder/Album Organization allows users to create named folders (albums) to organize their uploaded images. Images can be assigned to a folder during upload or moved between folders after upload. The gallery view adds folder navigation — users can browse the root level (showing folders and unorganized images), drill into a folder to see its contents, and navigate back via breadcrumbs. Each folder displays a count of its images and the most recent upload date. Folders can be renamed and deleted (with images optionally moved to root or deleted with the folder).

This feature introduces a new `Folder` JPA entity with a self-referencing parent-child relationship to support nested folders. The existing `ImageMetadata` entity gains a nullable `folderId` foreign key. The gallery, upload, and detail views are extended to be folder-aware. Storage keys in Azure Blob Storage remain flat (UUID-prefixed) — folder organization is a metadata-only concept, not reflected in blob paths.

## User Stories

### US-F011-001: Create a Folder

**As a** Content Manager
**I want to** create a named folder in the gallery
**So that** I can organize my images into logical groups

**Acceptance Criteria:**
- GIVEN I am on the gallery page WHEN I click "New Folder" and enter a name THEN a folder is created and appears in the gallery
- GIVEN I enter an empty folder name WHEN I submit THEN I see an error message and the folder is not created
- GIVEN a folder with the same name already exists at the same level WHEN I create a folder with that name THEN I see an error message about duplicate names

### US-F011-002: Browse Folders

**As a** Content Manager
**I want to** navigate into folders and see their contents
**So that** I can find images organized by topic or project

**Acceptance Criteria:**
- GIVEN folders exist in the gallery WHEN I view the gallery THEN I see folder cards alongside image cards
- GIVEN I click on a folder WHEN the folder page loads THEN I see only the images assigned to that folder
- GIVEN I am inside a folder WHEN I look at the breadcrumb navigation THEN I can navigate back to the parent folder or root

### US-F011-003: Upload Image to Folder

**As a** Content Manager
**I want to** upload an image directly into a specific folder
**So that** new images are organized immediately without a separate move step

**Acceptance Criteria:**
- GIVEN I am viewing a folder's contents WHEN I click "Upload New Image" THEN the uploaded image is assigned to that folder
- GIVEN I am at the root gallery WHEN I upload an image THEN the image has no folder assignment (root level)

### US-F011-004: Move Image Between Folders

**As a** Content Manager
**I want to** move an image from one folder to another (or to root)
**So that** I can reorganize my image collection

**Acceptance Criteria:**
- GIVEN I am on an image's detail page WHEN I select a destination folder from a dropdown and click "Move" THEN the image's folder assignment changes and I see a success message
- GIVEN I select "No Folder (Root)" WHEN I click Move THEN the image is moved to the root level

### US-F011-005: Rename a Folder

**As a** Content Manager
**I want to** rename an existing folder
**So that** I can correct typos or update the organization scheme

**Acceptance Criteria:**
- GIVEN I am viewing a folder WHEN I click "Rename" and enter a new name THEN the folder name is updated
- GIVEN I enter a name that already exists at the same level WHEN I submit THEN I see a duplicate name error

### US-F011-006: Delete a Folder

**As a** Content Manager
**I want to** delete a folder I no longer need
**So that** my gallery stays clean

**Acceptance Criteria:**
- GIVEN a folder contains images WHEN I delete the folder THEN the images are moved to the root level (not deleted) and the folder is removed
- GIVEN a folder is empty WHEN I delete it THEN the folder is removed immediately
- GIVEN I click delete WHEN a confirmation dialog appears and I cancel THEN the folder is not deleted

## Functional Requirements

### FR-F011-001: Folder Entity and Persistence

- Input: Folder name (String, required, max 255 chars), optional parent folder ID
- Processing: Create a `Folder` JPA entity with fields: `id` (Long, auto-generated), `name` (String, not null), `parentId` (Long, nullable — for nested folders), `createdAt` (LocalDateTime, auto-set), `lastModified` (LocalDateTime, auto-updated). Enforce unique constraint on `(name, parentId)` to prevent duplicate folder names at the same level.
- Output: Row in `folder` PostgreSQL table
- Error handling: `DataIntegrityViolationException` on duplicate name → user-friendly error message

### FR-F011-002: Image-Folder Assignment

- Input: Image metadata ID, folder ID (nullable)
- Processing: Add a `folderId` (Long, nullable) field to `ImageMetadata` entity. Null means the image is at the root level. When a folder is deleted, set `folderId = null` for all images in that folder (move to root).
- Output: Updated `image_metadata` row with folder assignment
- Error handling: Invalid folder ID → 404 response

### FR-F011-003: Folder CRUD Endpoints

- **POST /storage/folders** — Create a new folder. Accepts: `name` (form param), optional `parentId`. Returns: redirect to the folder's parent (or root).
- **GET /storage/folders/{id}** — View folder contents. Lists images with `folderId = id` and subfolders with `parentId = id`. Renders `list.html` with folder context.
- **POST /storage/folders/{id}/rename** — Rename a folder. Accepts: `name` (form param). Returns: redirect to folder.
- **POST /storage/folders/{id}/delete** — Delete a folder. Moves contained images to root. Deletes subfolders recursively (moving their images to root too). Returns: redirect to parent folder or root.
- **POST /storage/{key}/move** — Move an image to a different folder. Accepts: `folderId` (form param, nullable). Returns: redirect to image detail page.

### FR-F011-004: Folder-Aware Gallery Rendering

- Input: HTTP GET to `/storage` (root) or `/storage/folders/{id}` (specific folder)
- Processing: Query folders at the current level (`parentId = null` for root, `parentId = id` for subfolder). Query images at the current level (`folderId = null` for root, `folderId = id` for folder). Pass both `folders` and `objects` to the template.
- Output: `list.html` template rendering folder cards (with icon, name, image count) above image cards
- Error handling: Invalid folder ID → redirect to root with error

### FR-F011-005: Folder-Aware Upload

- Input: Upload request with optional `folderId` parameter
- Processing: If `folderId` is provided and valid, set the `folderId` on the new `ImageMetadata` record. If `folderId` is null or absent, image is at root.
- Output: Image stored with folder assignment
- Error handling: Invalid `folderId` → upload succeeds but image is at root level (graceful degradation)

### FR-F011-006: Breadcrumb Navigation

- Input: Current folder ID (or null for root)
- Processing: Build breadcrumb chain by traversing parent references from current folder to root. Return list of `(id, name)` pairs.
- Output: Breadcrumb HTML in gallery and folder views: `Root > Parent Folder > Current Folder`

## Non-Functional Requirements

### NFR-F011-001: Nesting Depth

Maximum folder nesting depth is 5 levels to prevent deep hierarchies and complex breadcrumb chains. Attempts to create folders beyond depth 5 should be rejected.

### NFR-F011-002: Folder Deletion Performance

Deleting a folder with many images must complete within a single database transaction. Images are moved to root (not deleted), so no storage operations are needed.

### NFR-F011-003: Backward Compatibility

Existing images with no folder assignment (`folderId = null`) must continue to appear at the root level. The migration must be non-destructive — no existing data is modified.

## Dependencies

| Dependency | Type | Direction | Description |
|------------|------|-----------|-------------|
| F-001 Image Upload | Feature | Upstream | Upload extended with optional folderId parameter |
| F-002 Image Gallery | Feature | Modified | Gallery renders folders + images; folder navigation added |
| F-003 Image Detail View | Feature | Modified | Detail view gains "Move to folder" action |
| F-005 Image Deletion | Feature | Upstream | Folder deletion moves images to root |
| F-008 Image Metadata Persistence | Feature | Modified | ImageMetadata gains folderId field |
| PostgreSQL | External | — | Folder table + ImageMetadata column |
| Spring Security | Feature | Upstream | Folder operations require authentication; delete requires ADMIN |

---

## Current Implementation (Brownfield Extension)

### Files to Create

| File Path | Role |
|-----------|------|
| `common/src/main/java/.../common/model/Folder.java` | JPA entity |
| `common/src/main/java/.../common/repository/FolderRepository.java` | Spring Data repository |
| `common/src/main/resources/db/migration/V3__add_folders.sql` | Flyway migration |
| `web/src/main/java/.../controller/FolderController.java` | Folder CRUD endpoints |
| `web/src/main/java/.../service/FolderService.java` | Folder business logic |

### Files to Modify

| File Path | Change |
|-----------|--------|
| `common/src/main/java/.../common/model/ImageMetadata.java` | Add `folderId` field |
| `common/src/main/java/.../common/repository/ImageMetadataRepository.java` | Add `findByFolderId()` query |
| `web/src/main/java/.../controller/StorageController.java` | Add `folderId` to upload, add move endpoint |
| `web/src/main/java/.../service/CloudStorageService.java` | Accept `folderId` in upload |
| `web/src/main/java/.../service/LocalFileStorageService.java` | Accept `folderId` in upload |
| `web/src/main/java/.../service/StorageService.java` | Update `uploadObject()` signature |
| `web/src/main/resources/templates/list.html` | Add folder cards, breadcrumbs, "New Folder" button |
| `web/src/main/resources/templates/upload.html` | Add hidden `folderId` field |
| `web/src/main/resources/templates/view.html` | Add "Move to folder" dropdown |

### Architecture Pattern

MVC with service layer. New `FolderController` + `FolderService` for folder CRUD. `StorageController` extended for folder-aware upload and move. Folder hierarchy is metadata-only — blob storage keys remain flat.

### Test Coverage

| Test Type | Files | Assertions | Coverage |
|-----------|-------|------------|----------|
| Unit | To be created | 0 | 0% |

### Database Migration

```sql
-- V3__add_folders.sql
CREATE TABLE IF NOT EXISTS folder (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT REFERENCES folder(id) ON DELETE CASCADE,
    created_at TIMESTAMP,
    last_modified TIMESTAMP,
    UNIQUE(name, parent_id)
);

ALTER TABLE image_metadata ADD COLUMN IF NOT EXISTS folder_id BIGINT REFERENCES folder(id) ON DELETE SET NULL;
```

### Known Limitations

- Folder nesting depth is not enforced at the database level — must be validated in the service layer
- Moving a folder (reparenting) is not included in this feature — only rename and delete
- Folder thumbnails (showing a preview of contained images) are not included — folders show a generic icon
- No drag-and-drop moving of images between folders — only via the detail page dropdown

### Integration Points

| External System | Protocol | Purpose | Config Location |
|----------------|----------|---------|-----------------|
| PostgreSQL | JDBC | Folder table + ImageMetadata.folderId | `application.properties` |
