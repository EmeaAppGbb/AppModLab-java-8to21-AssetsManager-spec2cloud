package com.microsoft.migration.assets.worker.model;

/**
 * Immutable message DTO for image processing queue using Java 21 record.
 */
public record ImageProcessingMessage(
    String key,
    String contentType,
    String storageType,
    long size
) {}