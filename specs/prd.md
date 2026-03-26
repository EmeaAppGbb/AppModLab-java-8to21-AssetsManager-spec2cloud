# Product Requirements Document

## Product Vision

Assets Manager is a web-based image asset management application that enables users to upload, browse, view, and delete image files stored in cloud object storage (AWS S3) or a local filesystem. When an image is uploaded, the system asynchronously generates a high-quality thumbnail via a background worker service connected through a RabbitMQ message queue, and persists image metadata (including thumbnail references) in a PostgreSQL database. The application provides a responsive Bootstrap-based UI with drag-and-drop upload, image gallery with auto-refresh polling for thumbnail availability, and a detail view with download capability. It is designed as a two-module Spring Boot system (web + worker) with profile-based storage backend switching for development and production environments.

## User Personas

### Content Manager (Primary User)

- **Role**: Any user who needs to upload, organize, view, and manage image assets
- **Needs**: Simple web interface to upload images, browse a gallery of uploaded images, view full-size images with metadata, download originals, and delete unwanted files
- **Goals**: Quickly upload and manage image assets; see thumbnails generated automatically; find and retrieve previously uploaded images
- **Source**: Inferred from UI routes and Thymeleaf templates — the entire web module serves a single user type with full CRUD access. No authentication or role differentiation exists in the codebase.

### System Operator

- **Role**: Developer or operator who configures and deploys the application
- **Needs**: Ability to switch between S3 and local storage via Spring profiles; configure AWS credentials, database connection, and RabbitMQ settings; monitor file operations through logging
- **Goals**: Deploy and maintain the application across development and production environments; ensure infrastructure connectivity (S3, PostgreSQL, RabbitMQ)
- **Source**: Inferred from Spring profile configuration (`@Profile("dev")` / `@Profile("!dev")`), environment variable placeholders in `application.properties`, `FileOperationLoggingInterceptor`, and devcontainer configurations for JDK 8 and JDK 21.

## Feature List

| ID | Feature | Description | Priority | Dependencies |
|----|---------|-------------|----------|--------------|
| F-001 | Image Upload | Upload image files via a web form with drag-and-drop support and client-side preview. Files are stored in S3 (production) or local filesystem (dev). Max file size: 10MB. Client-side filtering to `image/*` MIME types. On successful upload, a message is dispatched to the `image-processing` RabbitMQ queue for thumbnail generation, and metadata is persisted to PostgreSQL. | P0 (Critical) | — |
| F-002 | Image Gallery | Display all uploaded images in a responsive Bootstrap card grid. Each card shows a thumbnail preview, filename (truncated), file size in KB, and last-modified date. Includes auto-refresh polling (3–6 second intervals) that detects new thumbnails by checking for `_thumbnail` suffixed files. Uses `sessionStorage` to track recent uploads and adapt polling frequency. | P0 (Critical) | F-001 |
| F-003 | Image Detail View | View a single image at full size with metadata (file size, upload date, last modified date). Accessed via the gallery card "View" button. Includes breadcrumb navigation back to the gallery. | P1 (High) | F-002 |
| F-004 | Image Download | Download the original image file via a direct streaming endpoint (`/storage/view/{key}`) that returns an `InputStreamResource` with `application/octet-stream` content type. Available from the detail view page as a download button. | P1 (High) | F-003 |
| F-005 | Image Deletion | Delete an image and its associated thumbnail from storage (S3 or local). Includes JavaScript confirmation dialog before deletion. Removes metadata from PostgreSQL. Redirects to gallery with success/error flash message. | P1 (High) | F-001 |
| F-006 | Async Thumbnail Generation | Background worker consumes messages from the `image-processing` RabbitMQ queue. For each image: downloads the original, generates a 600px-max-dimension thumbnail using progressive multi-step scaling with bicubic interpolation and sharpening filter, uploads the thumbnail back to storage, and updates the `thumbnailKey`/`thumbnailUrl` fields in the database. Supports JPEG (95% quality) and PNG (lossless) output. Manual message acknowledgment with dead-letter routing on failure. | P0 (Critical) | F-001 |
| F-007 | Dual Storage Backend | Strategy pattern with two implementations: `AwsS3Service` (active when profile ≠ dev) using AWS SDK v2 S3Client, and `LocalFileStorageService` (active when profile = dev) using `java.nio.file` APIs. Both implement the `StorageService` interface. Worker module mirrors this with `S3FileProcessingService` and `LocalFileProcessingService`. Storage type is embedded in RabbitMQ messages so the worker processes only matching messages. | P1 (High) | — |
| F-008 | Image Metadata Persistence | JPA entity `ImageMetadata` stored in PostgreSQL with fields: id, filename, contentType, size, s3Key, s3Url, thumbnailKey, thumbnailUrl, uploadedAt, lastModified. Auto-timestamps via `@PrePersist`/`@PreUpdate` lifecycle callbacks. Repository provides standard CRUD via Spring Data `JpaRepository`. | P1 (High) | — |
| F-009 | File Operation Logging | `FileOperationLoggingInterceptor` in `WebMvcConfig` logs all requests to `/storage/**` (excluding `/storage/view/**`). Captures: HTTP method, URI, operation type (FILE_UPLOAD, FILE_DELETE, FILE_DOWNLOAD, FILE_VIEW_PAGE, FILE_LIST), start time, duration in ms, response status, and error messages. | P2 (Medium) | — |
| F-010 | Backup Message Processor | Optional RabbitMQ consumer (`BackupMessageProcessor`) activated only when the `backup` Spring profile is active. Listens on the same `image-processing` queue for monitoring/logging purposes. Acknowledges messages after logging metadata. | P3 (Low) | F-006 |

## Non-Functional Requirements

### Performance

- **File upload limit**: 10MB per file and per request (`spring.servlet.multipart.max-file-size=10MB`). Source: `web/src/main/resources/application.properties`.
- **Thumbnail dimensions**: Max 600px on longest dimension with aspect ratio preservation. Progressive multi-step scaling (no more than 50% reduction per step) for quality. Source: `worker/../service/AbstractFileProcessingService.java`.
- **Caching**: Static resources (CSS, JS, images) cached for 30 days; favicon cached for 7 days. Source: `web/../config/WebMvcConfig.java`.
- **Polling**: Client-side polling at 3s during active uploads, 6s during idle, stops aggressive polling after 30s of no changes. Source: `web/src/main/resources/templates/list.html`.

### Security

- **Authentication**: None. No Spring Security dependency, no login page, no role-based access. All endpoints are publicly accessible. Source: absence of `spring-boot-starter-security` in `web/pom.xml`.
- **Input validation**: Client-side only (`accept="image/*"` on file input). No server-side content-type validation or filename sanitization beyond `StringUtils.cleanPath()` in `LocalFileStorageService`. Source: `web/../controller/S3Controller.java`, `web/../service/LocalFileStorageService.java`.
- **Credential management**: AWS credentials, database passwords, and RabbitMQ passwords stored as placeholder values in `application.properties`. Environment variable overrides available for host names only. Source: `web/src/main/resources/application.properties`, `worker/src/main/resources/application.properties`.

### Reliability

- **Message acknowledgment**: Manual ACK mode for RabbitMQ. Failed messages are NACK'd without requeue (routed to dead-letter exchange if configured). Source: `worker/../config/RabbitConfig.java`, `worker/../service/AbstractFileProcessingService.java`.
- **Durable queue**: `image-processing` queue is durable (survives broker restart). Source: `worker/../config/RabbitConfig.java`.
- **Temporary file cleanup**: Worker always cleans up temp files in a `finally` block. Source: `worker/../service/AbstractFileProcessingService.java`.
- **Error handling**: Controller-level try/catch with flash attribute error messages. No global `@ControllerAdvice`. No custom error pages.

### Scalability

- **Module separation**: Web and worker are separate Spring Boot applications communicating via RabbitMQ, allowing independent scaling.
- **S3 storage**: Production mode uses AWS S3, which is inherently scalable.
- **Database**: Shared PostgreSQL instance between web and worker. No connection pooling configuration beyond Spring Boot defaults.
- **No pagination**: S3 object listing and gallery rendering are unbounded — all objects loaded at once.

### Observability

- **Logging**: SLF4J/Logback in services (via Lombok `@Slf4j` and manual `LoggerFactory`). `System.out.printf` in the file operation interceptor. No structured logging format. Source: various service classes.
- **PID file**: Both web and worker write an `application.pid` file via `ApplicationPidFileWriter` for process management. Source: `AssetsManagerApplication.java`, `WorkerApplication.java`.
- **SQL logging**: `spring.jpa.show-sql=true` in web module. Source: `web/src/main/resources/application.properties`.
- **No APM**: No application performance monitoring, metrics endpoint, or health check endpoint configured.

## Out of Scope

The following capabilities are **not implemented** in the current codebase:

- **User authentication and authorization** — No login, registration, roles, or access control
- **Multi-tenant support** — No workspace or organization concept; single shared storage
- **Image editing or transformation** — Only thumbnail generation; no crop, resize, rotate, or filter tools
- **Search and filtering** — No search bar, tag system, or filter controls in the gallery
- **Folder/directory organization** — All images are stored flat; no folder hierarchy
- **Batch operations** — No multi-select, bulk delete, or bulk download
- **API layer** — No REST/GraphQL API for programmatic access; only server-rendered HTML
- **Notifications** — No email, webhook, or push notification on upload completion or errors
- **Versioning** — No file versioning or history; uploads with the same name overwrite in dev mode
- **CDN integration** — No CloudFront or CDN layer for serving images
- **Rate limiting** — No throttling on upload or download endpoints
- **Pagination** — Gallery displays all images at once; no pagination or infinite scroll
- **Health checks** — No Spring Actuator or custom health endpoint

## Appendix: Extraction Evidence

| PRD Section | Evidence Source |
|-------------|----------------|
| Product Vision | `web/pom.xml` (description: "Web module for assets manager that handles file uploads and viewing"), `worker/pom.xml` (description: "Worker module for assets manager that handles thumbnail generation"), Thymeleaf templates title "AWS S3 Asset Manager" |
| Content Manager persona | `web/../controller/S3Controller.java` (all CRUD routes public, no auth), `web/src/main/resources/templates/*.html` (UI designed for direct user interaction) |
| System Operator persona | `web/src/main/resources/application.properties` (env var placeholders), `.devcontainer/jdk8/devcontainer.json` and `.devcontainer/jdk21/devcontainer.json` (infrastructure config), `@Profile` annotations across service classes |
| F-001 Image Upload | `S3Controller.uploadObject()`, `AwsS3Service.uploadObject()`, `LocalFileStorageService.uploadObject()`, `upload.html` template |
| F-002 Image Gallery | `S3Controller.listObjects()`, `list.html` template (including JavaScript polling logic) |
| F-003 Image Detail View | `S3Controller.viewObjectPage()`, `view.html` template |
| F-004 Image Download | `S3Controller.viewObject()` returning `ResponseEntity<InputStreamResource>` |
| F-005 Image Deletion | `S3Controller.deleteObject()`, `AwsS3Service.deleteObject()`, `LocalFileStorageService.deleteObject()` |
| F-006 Async Thumbnail Generation | `worker/../service/AbstractFileProcessingService.processImage()`, `worker/../service/S3FileProcessingService`, `worker/../service/LocalFileProcessingService`, `RabbitConfig` in both modules |
| F-007 Dual Storage Backend | `@Profile("dev")` on `LocalFileStorageService`/`LocalFileProcessingService`, `@Profile("!dev")` on `AwsS3Service`/`S3FileProcessingService`, `StorageService` interface, `FileProcessor` interface |
| F-008 Image Metadata | `ImageMetadata.java` (JPA entity in both modules), `ImageMetadataRepository.java`, `application.properties` database config |
| F-009 File Operation Logging | `WebMvcConfig.FileOperationLoggingInterceptor` inner class |
| F-010 Backup Message Processor | `web/../service/BackupMessageProcessor.java` with `@Profile("backup")` |
| Non-Functional Requirements | `application.properties` (both modules), `WebMvcConfig.java` (caching), `list.html` (polling), `AbstractFileProcessingService.java` (thumbnail config) |
| Out of Scope | Absence of: `spring-boot-starter-security`, search UI components, folder models, batch endpoints, REST API controllers, actuator dependency, pagination parameters |
