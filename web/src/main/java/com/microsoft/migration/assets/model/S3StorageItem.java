package com.microsoft.migration.assets.model;

import java.time.Instant;

/**
 * Immutable storage item representation using Java 21 record.
 */
public record S3StorageItem(
    String key,
    String name,
    long size,
    Instant lastModified,
    Instant uploadedAt,
    String url
) {}