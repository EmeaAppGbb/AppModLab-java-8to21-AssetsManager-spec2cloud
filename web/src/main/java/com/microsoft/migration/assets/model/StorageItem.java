package com.microsoft.migration.assets.model;

import java.time.Instant;

public record StorageItem(
    String key,
    String name,
    long size,
    Instant lastModified,
    Instant uploadedAt,
    String url,
    Long folderId
) {}