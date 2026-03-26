# ADR-003: Create Shared Maven Module for Duplicated Code

## Status

Proposed

## Context

The `web` and `worker` modules contain **identical classes** in different packages:

| Class | Web Location | Worker Location |
|-------|-------------|-----------------|
| `ImageMetadata` (JPA entity) | `com.microsoft.migration.assets.model` | `com.microsoft.migration.assets.worker.model` |
| `ImageProcessingMessage` (DTO) | `com.microsoft.migration.assets.model` | `com.microsoft.migration.assets.worker.model` |
| `ImageMetadataRepository` (JPA) | `com.microsoft.migration.assets.repository` | `com.microsoft.migration.assets.worker.repository` |

Additionally, the `StorageUtil` utility in the worker module duplicates the `getThumbnailKey()` logic from the `StorageService` default method in the web module.

### Modernization Assessment Findings

- **A2** (High): Duplicated model classes across modules — drift risk
- **A5** (Medium): Inconsistent property names across modules (symptom of duplication)

### Options Considered

#### Option A: Create a Shared `common` Maven Module (Recommended)

- **Pros**: Single source of truth for shared models; compile-time contract enforcement; eliminates drift risk
- **Cons**: Adds a third Maven module; slightly more complex build

#### Option B: Keep Duplicated Code

- **Pros**: No structural changes; modules remain fully independent
- **Cons**: Ongoing drift risk; changes must be synchronized manually; bugs can hide in diverging copies

#### Option C: Publish Shared Models as a Separate Library

- **Pros**: Full decoupling with versioned releases
- **Cons**: Overkill for an internal project with two consumers; adds release management overhead

## Decision

**Create a shared `common` Maven module** (Option A).

## Rationale

1. The duplicated classes are identical today, but any future change must be applied in two places manually.
2. The `ImageProcessingMessage` is the RabbitMQ contract between web and worker — it MUST be shared to avoid silent deserialization failures.
3. A Maven module within the same multi-module project is lightweight and doesn't add deployment complexity.
4. The shared module would contain: `ImageMetadata`, `ImageProcessingMessage`, `ImageMetadataRepository`, `StorageUtil`, and shared constants.

## Proposed Structure

```
assets-manager-parent/
├── common/                    ← NEW shared module
│   ├── pom.xml
│   └── src/main/java/com/microsoft/migration/assets/common/
│       ├── model/
│       │   ├── ImageMetadata.java
│       │   └── ImageProcessingMessage.java
│       ├── repository/
│       │   └── ImageMetadataRepository.java
│       └── util/
│           └── StorageUtil.java
├── web/
│   └── pom.xml               ← depends on common
├── worker/
│   └── pom.xml               ← depends on common
└── pom.xml                   ← lists common in <modules>
```

## Consequences

- **Positive**: Eliminates code duplication; enforces shared contract; reduces maintenance burden
- **Negative**: Adds a third module; both web and worker must depend on `common`
- **Migration effort**: Hours (move existing code, update imports, verify build)
- **Timing**: Should be done early in the modernization roadmap (Phase 1) to simplify subsequent changes

## References

- Assessment findings: A2, A5
