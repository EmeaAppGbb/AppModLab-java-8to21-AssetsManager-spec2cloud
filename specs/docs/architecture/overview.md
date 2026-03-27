# Architecture Overview — Assets Manager

_Extracted on 2026-03-27. Documents the architecture as it exists in code._

## System Boundaries

The Assets Manager is a **multi-module Maven monorepo** containing three modules that produce two independently deployable Spring Boot applications plus one shared library:

| Module | Artifact | Type | Runtime | Entry Point | Port |
|--------|----------|------|---------|-------------|------|
| **common** | `assets-manager-common` | JAR (library) | N/A — dependency only | — | — |
| **web** | `assets-manager-web` | Spring Boot executable JAR | Java 21 (Temurin) | `AssetsManagerApplication.main()` | 8080 |
| **worker** | `assets-manager-worker` | Spring Boot executable JAR | Java 21 (Temurin) | `WorkerApplication.main()` | — (no HTTP) |

**Framework:** Spring Boot 3.4.4, Java 21 LTS, Maven 3.9.9

## High-Level Architecture

```mermaid
graph TD
    subgraph "Client"
        Browser["Web Browser"]
    end

    subgraph "Web Module (assets-manager-web)"
        HC["HomeController"]
        SC["StorageController"]
        CSS["CloudStorageService<br/>@Profile(!dev)"]
        LFSS["LocalFileStorageService<br/>@Profile(dev)"]
        BMP["BackupMessageProcessor<br/>@Profile(backup)"]
        WMC["WebMvcConfig<br/>FileOperationLoggingInterceptor"]
        CSC_W["CloudStorageConfig"]
        RC_W["RabbitConfig"]
    end

    subgraph "Worker Module (assets-manager-worker)"
        AFPS["AbstractFileProcessingService<br/>@RabbitListener"]
        CFPS["CloudFileProcessingService<br/>@Profile(!dev)"]
        LFPS["LocalFileProcessingService<br/>@Profile(dev)"]
        CSC_WK["CloudStorageConfig"]
        RC_WK["RabbitConfig"]
    end

    subgraph "Common Module (assets-manager-common)"
        IM["ImageMetadata<br/>JPA Entity"]
        IPM["ImageProcessingMessage<br/>Java Record"]
        IMR["ImageMetadataRepository<br/>JpaRepository"]
        SU["StorageUtil"]
        FW["Flyway<br/>V1__initial_schema.sql"]
    end

    subgraph "External Services"
        Azure["Azure Blob Storage"]
        RMQ["RabbitMQ<br/>queue: image-processing"]
        PG["PostgreSQL<br/>db: assets_manager"]
        FS["Local Filesystem<br/>../storage"]
    end

    Browser -->|HTTP| WMC
    WMC -->|intercepts| SC
    Browser -->|HTTP GET /| HC
    HC -->|redirect /storage| SC

    SC -->|delegates| CSS
    SC -->|delegates| LFSS

    CSS -->|upload/download/list/delete| Azure
    CSS -->|send message| RMQ
    CSS -->|CRUD metadata| PG

    LFSS -->|read/write files| FS
    LFSS -->|send message| RMQ

    BMP -->|consume messages| RMQ

    RMQ -->|deliver message| AFPS
    AFPS -->|delegates| CFPS
    AFPS -->|delegates| LFPS

    CFPS -->|download/upload blobs| Azure
    CFPS -->|update metadata| PG

    LFPS -->|read/write files| FS

    CSS -.->|uses| IM
    CSS -.->|uses| IPM
    CSS -.->|uses| IMR
    CFPS -.->|uses| IMR
    AFPS -.->|uses| IPM
    AFPS -.->|uses| SU
    FW -.->|migrates schema for| PG
```

The system follows a **producer-consumer pattern** with two independent Spring Boot applications communicating asynchronously via a RabbitMQ message queue. The web module handles user-facing HTTP requests and storage operations. The worker module consumes image-processing messages and generates thumbnails. Both modules share domain models, the JPA repository, and utilities via the common module.

Storage backend selection is controlled by **Spring profiles**: the `dev` profile activates local filesystem implementations, while the default profile (`!dev`) activates Azure Blob Storage implementations. Both modules use the same profile-switching mechanism independently.

## Data Flow

### Primary Flow: Image Upload → Thumbnail Generation

```mermaid
sequenceDiagram
    participant B as Browser
    participant SC as StorageController
    participant SS as CloudStorageService
    participant AZ as Azure Blob Storage
    participant RMQ as RabbitMQ
    participant PG as PostgreSQL
    participant WK as AbstractFileProcessingService
    participant CFPS as CloudFileProcessingService

    B->>SC: POST /storage/upload (multipart file)
    SC->>SS: uploadObject(file)
    SS->>AZ: BlobClient.upload(inputStream, size)
    SS->>RMQ: convertAndSend("image-processing", message)
    SS->>PG: imageMetadataRepository.save(metadata)
    SS-->>SC: void
    SC-->>B: redirect /storage (flash: "success")

    Note over RMQ,WK: Async — separate process
    RMQ->>WK: processImage(message, channel, deliveryTag)
    WK->>WK: Check storageType matches
    WK->>CFPS: downloadOriginal(key, tempPath)
    CFPS->>AZ: BlobClient.downloadToFile(tempPath)
    WK->>WK: generateThumbnail(original, thumbnail)
    Note over WK: Progressive scaling → Sharpen → Format-specific output
    WK->>CFPS: uploadThumbnail(thumbnailPath, thumbnailKey, contentType)
    CFPS->>AZ: BlobClient.uploadFromFile(path)
    CFPS->>PG: findByStorageKey → update thumbnailKey/Url → save
    WK->>RMQ: channel.basicAck(deliveryTag)
    WK->>WK: cleanup temp files
```

### Secondary Flow: Image Gallery Browsing

```mermaid
sequenceDiagram
    participant B as Browser
    participant SC as StorageController
    participant SS as CloudStorageService
    participant AZ as Azure Blob Storage
    participant PG as PostgreSQL

    B->>SC: GET /storage
    SC->>SS: listObjects()
    SS->>AZ: blobContainerClient.listBlobs()
    SS->>PG: imageMetadataRepository.findAll() → Map by storageKey
    SS-->>SC: List<StorageItem>
    SC-->>B: list.html (Thymeleaf rendered)

    loop Auto-refresh polling (every 3-6s)
        B->>SC: GET /storage (fetch API)
        SC->>SS: listObjects()
        SS-->>SC: List<StorageItem>
        SC-->>B: Updated HTML (diff-check for new thumbnails)
    end
```

### Tertiary Flow: Image Download / View

```mermaid
sequenceDiagram
    participant B as Browser
    participant SC as StorageController
    participant SS as CloudStorageService
    participant AZ as Azure Blob Storage

    B->>SC: GET /storage/view/{key}
    SC->>SC: validateKey(key)
    SC->>SS: getObject(key)
    SS->>AZ: BlobClient.openInputStream()
    SS-->>SC: InputStream
    SC-->>B: ResponseEntity<InputStreamResource> (application/octet-stream)
```

### Error Flow: Message Processing Failure

```mermaid
sequenceDiagram
    participant RMQ as RabbitMQ
    participant WK as AbstractFileProcessingService
    participant DLX as Dead Letter Exchange

    RMQ->>WK: processImage(message)
    WK->>WK: Processing throws Exception
    WK->>WK: log.error("Failed to process image: {}", key, e)
    WK->>WK: cleanup temp files (finally block)
    WK->>RMQ: channel.basicNack(deliveryTag, false, false)
    RMQ->>DLX: Route failed message (requeue=false)
```

## Integration Points

| Type | Technology | Used By | Config Source | Protocol |
|------|-----------|---------|---------------|----------|
| Object Storage | Azure Blob Storage | `CloudStorageService` (web), `CloudFileProcessingService` (worker) | `azure.storage.connection-string` env var | HTTPS (Azure SDK) |
| Message Queue | RabbitMQ | `CloudStorageService` / `LocalFileStorageService` (producer), `AbstractFileProcessingService` (consumer), `BackupMessageProcessor` (monitor) | `spring.rabbitmq.*` env vars | AMQP 0.9.1 |
| Database | PostgreSQL 16 | `ImageMetadataRepository` via `CloudStorageService` (web), `CloudFileProcessingService` (worker) | `spring.datasource.*` env vars | JDBC |
| Local Storage | Filesystem (`../storage`) | `LocalFileStorageService` (web), `LocalFileProcessingService` (worker) | `local.storage.directory` property | File I/O (NIO.2) |
| Schema Migration | Flyway | `common` module (auto-run on startup) | `db/migration/V1__initial_schema.sql` | SQL over JDBC |
| API Documentation | Springdoc OpenAPI 2.8.6 | `StorageController` (web) | `springdoc.*` properties | HTTP (`/swagger-ui.html`, `/v3/api-docs`) |
| Frontend Framework | Bootstrap 5.3 | Thymeleaf templates (web) | CDN link in `layout.html` | HTTPS (CDN) |
| Image Processing | Java AWT + ImageIO | `AbstractFileProcessingService` (worker) | Hardcoded: max 600px, JPEG 0.95, PNG lossless | In-process |

## Architectural Patterns Observed

### Multi-Module Monorepo
Three Maven modules (`common`, `web`, `worker`) in a single repository. `common` is a shared library; `web` and `worker` are independent Spring Boot applications. Evidence: root `pom.xml` `<modules>` declaration; `common` listed as a `<dependency>` in both `web/pom.xml` and `worker/pom.xml`.

### Strategy Pattern (Storage Backend)
Web module: `StorageService` interface with `CloudStorageService` (`@Profile("!dev")`) and `LocalFileStorageService` (`@Profile("dev")`). Worker module: `FileProcessor` interface with `CloudFileProcessingService` (`@Profile("!dev")`) and `LocalFileProcessingService` (`@Profile("dev")`). Profile-based bean selection at startup — no runtime switching.

### Template Method Pattern (Worker Processing)
`AbstractFileProcessingService` defines the image processing pipeline (`processImage` → download → thumbnail → upload → ack). Subclasses implement storage-specific operations (`downloadOriginal`, `uploadThumbnail`, `generateUrl`, `getStorageType`). Evidence: `AbstractFileProcessingService` is abstract with concrete `processImage()` and abstract protected methods.

### MVC (Web Module)
Controllers (`HomeController`, `StorageController`) handle HTTP routing. Services (`CloudStorageService`, `LocalFileStorageService`) contain business logic. Views are Thymeleaf templates (`layout.html`, `list.html`, `upload.html`, `view.html`). Models are `StorageItem` (web DTO) and `ImageMetadata` (JPA entity from common). Evidence: `@Controller`, `@Service`, and `@Entity` annotations; templates in `resources/templates/`.

### Producer-Consumer (Asynchronous Processing)
Web module produces `ImageProcessingMessage` to the `image-processing` RabbitMQ queue after every upload. Worker module consumes messages via `@RabbitListener`. Manual acknowledgment mode with dead-letter routing on failure. Evidence: `RabbitTemplate.convertAndSend()` in services; `@RabbitListener(queues = IMAGE_PROCESSING_QUEUE)` in `AbstractFileProcessingService`; `AcknowledgeMode.MANUAL` in both `RabbitConfig` classes.

### Shared Kernel (Common Module)
Domain model (`ImageMetadata`, `ImageProcessingMessage`), repository (`ImageMetadataRepository`), utilities (`StorageUtil`), and schema migrations (`Flyway`) are centralized in `common`. Both `web` and `worker` depend on it. Evidence: `@EntityScan` and `@EnableJpaRepositories` pointing to `com.microsoft.migration.assets.common.*` in both application classes.

### Interceptor Chain (Cross-Cutting Logging)
`FileOperationLoggingInterceptor` (inner class of `WebMvcConfig`) intercepts all `/storage/**` requests (excluding `/storage/view/**`). Logs operation type, duration, and status. Evidence: `WebMvcConfigurer.addInterceptors()` with path patterns in `WebMvcConfig.java`.
