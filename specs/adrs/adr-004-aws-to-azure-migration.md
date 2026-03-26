# ADR-004: Migrate from AWS to Azure Cloud Services

## Status

Proposed

## Context

The application currently uses **AWS S3** for object storage via the AWS SDK for Java v2 (`software.amazon.awssdk:s3:2.25.13`). Additionally, it uses **RabbitMQ** for message queuing (self-hosted, not an AWS service) and **PostgreSQL** for metadata persistence (also not AWS-specific).

The project modernization goal includes moving to **Azure** as the target cloud platform. This requires replacing AWS S3 with Azure Blob Storage, and evaluating whether RabbitMQ should be replaced with Azure Service Bus for a fully Azure-native stack.

### AWS Reference Inventory

The codebase contains **60+ references** to AWS/S3 across 6 Java files, 2 config files, 2 POMs, 3 templates, and 1 test file:

- **22 AWS SDK imports** across config, service, and test classes
- **8 AWS configuration properties** (`aws.accessKey`, `aws.secretKey`, `aws.region`, `aws.s3.bucket`)
- **4 POM dependency entries** (`software.amazon.awssdk:s3`)
- **9 S3-specific types** used (S3Client, ListObjectsV2Request/Response, PutObjectRequest, GetObjectRequest, DeleteObjectRequest, GetUrlRequest, RequestBody)
- **4 classes named with "S3/Aws"** (AwsS3Config ×2, AwsS3Service, S3FileProcessingService)
- **6 model fields** with "s3" prefix (s3Key, s3Url in ImageMetadata; S3StorageItem class)
- **6 template references** to "AWS S3" / "S3" in user-visible text
- **3 string literals** returning "s3" as storage type

### Options Considered

#### Storage: AWS S3 → Azure Blob Storage

| Aspect | AWS S3 | Azure Blob Storage |
|--------|--------|-------------------|
| SDK | `software.amazon.awssdk:s3` | `com.azure:azure-storage-blob` + `spring-cloud-azure-starter-storage-blob` |
| Client | `S3Client` | `BlobServiceClient` / `BlobContainerClient` |
| Auth | `AwsBasicCredentials` + `StaticCredentialsProvider` | `StorageSharedKeyCredential` or `DefaultAzureCredential` (Azure AD) |
| Config | `aws.accessKey`, `aws.s3.bucket` | `spring.cloud.azure.storage.blob.account-name`, `container-name` |
| Spring Integration | Manual `@Bean` config | Auto-configured via Spring Cloud Azure starter |
| Latest Stable | 2.42.21 | 12.33.2 (blob SDK) / 5.11.0 (Spring Cloud Azure) |

#### Messaging: RabbitMQ → Azure Service Bus (Optional)

| Aspect | RabbitMQ (Current) | Azure Service Bus |
|--------|-------------------|-------------------|
| Type | Self-hosted / container | Fully managed PaaS |
| Protocol | AMQP 0.9.1 | AMQP 1.0 |
| Spring Integration | `spring-boot-starter-amqp` | `spring-cloud-azure-starter-servicebus` |
| Operations | Self-managed | Zero-ops (Azure manages) |
| Features | Plugins, manual DLQ | Built-in DLQ, dedup, scheduled delivery, sessions |
| Migration Effort | None (keep as-is) | Moderate (new listener patterns, new config) |

#### Option A: Migrate Storage Only (S3 → Blob Storage), Keep RabbitMQ

- **Pros**: Smaller migration scope; RabbitMQ is portable and works in containers on Azure; Spring AMQP code unchanged
- **Cons**: Still requires a self-managed RabbitMQ instance (container or Azure marketplace)

#### Option B: Full Azure Migration (S3 → Blob Storage + RabbitMQ → Service Bus)

- **Pros**: Fully Azure-native; zero message broker operations; enterprise features built-in
- **Cons**: Larger migration; AMQP 0.9.1 → 1.0 differences; Spring Cloud Azure listener patterns differ from Spring AMQP

#### Option C: Full Azure Migration with RabbitMQ on Azure (S3 → Blob + RabbitMQ on ACA/Container)

- **Pros**: Storage is Azure-native; messaging code unchanged; RabbitMQ runs as a container on Azure Container Apps
- **Cons**: Still self-managing RabbitMQ, but Azure hosts the container

#### Database: PostgreSQL

PostgreSQL is cloud-agnostic. No code changes needed — only deployment infrastructure changes (provision Azure Database for PostgreSQL Flexible Server). This is an infrastructure concern handled at deployment time, not a code migration item.

## Decision

**Option A: Migrate storage from AWS S3 to Azure Blob Storage. Keep RabbitMQ** (portable, works in Azure containers).

## Rationale

1. S3 → Azure Blob Storage is the primary cloud migration — all AWS SDK references must be replaced.
2. RabbitMQ is not AWS-specific. It runs natively in Docker containers, which can be deployed on Azure Container Apps or AKS with zero code changes.
3. Migrating both storage AND messaging simultaneously increases risk. The storage migration alone involves 60+ code references across 15+ files.
4. RabbitMQ → Azure Service Bus can be a future increment if desired (the plan leaves room for this).
5. Spring Cloud Azure provides excellent auto-configuration for Blob Storage, reducing boilerplate.
6. Azure AD authentication (`DefaultAzureCredential`) is more secure than static keys and aligns with Azure best practices.

## Migration Approach

The migration is split into two increments:

### Increment 1: Rename S3-specific identifiers to cloud-agnostic names

Before swapping the SDK, rename all S3/AWS-branded names to generic names:
- `S3Controller` → `StorageController`
- `S3StorageItem` → `StorageItem`
- `AwsS3Service` → (removed, replaced by Azure impl in next step)
- `AwsS3Config` → (removed, replaced by Azure config in next step)
- `s3Key` → `storageKey` (field in `ImageMetadata`)
- `s3Url` → `storageUrl` (field in `ImageMetadata`)
- `S3FileProcessingService` → (will become `BlobFileProcessingService`)
- Template text: "AWS S3 Asset Manager" → "Asset Manager"
- Storage type string: `"s3"` → `"blob"` or `"cloud"`

### Increment 2: Replace AWS SDK with Azure Blob Storage SDK

- Replace Maven deps: `software.amazon.awssdk:s3` → `com.azure:azure-storage-blob` + `spring-cloud-azure-starter-storage-blob`
- Replace `AwsS3Config` → `AzureBlobConfig` (or use Spring Cloud Azure auto-config)
- Replace `AwsS3Service` → `AzureBlobStorageService` implementing `StorageService`
- Replace `S3FileProcessingService` → `BlobFileProcessingService` implementing `FileProcessor`
- Update `application.properties`: `aws.*` → `spring.cloud.azure.storage.blob.*`
- Update tests: mock `BlobContainerClient` instead of `S3Client`

## Consequences

- **Positive**: Fully Azure-native storage; Azure AD auth; Spring Cloud Azure auto-configuration; no AWS SDK dependency
- **Negative**: All storage-related code must be rewritten; existing tests must be updated; developers need Azure Blob Storage knowledge
- **Migration effort**: 2-3 days for name refactoring, 3-5 days for SDK replacement and testing
- **Future option**: RabbitMQ → Azure Service Bus can be added as a subsequent increment if full Azure-native messaging is desired

## References

- Assessment findings: S1 (credentials), mod-010 (AWS SDK update — superseded by this migration)
- Azure Blob Storage Java SDK: https://learn.microsoft.com/en-us/java/api/overview/azure/storage-blob-readme
- Spring Cloud Azure Storage: https://learn.microsoft.com/en-us/azure/developer/java/spring-framework/configure-spring-boot-starter-java-app-with-azure-storage
