package com.microsoft.migration.assets.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ImageProcessingMessage(
    String key,
    String contentType,
    String storageType,
    long size
) {
    @JsonCreator
    public ImageProcessingMessage(
        @JsonProperty("key") String key,
        @JsonProperty("contentType") String contentType,
        @JsonProperty("storageType") String storageType,
        @JsonProperty("size") long size
    ) {
        this.key = key;
        this.contentType = contentType;
        this.storageType = storageType;
        this.size = size;
    }
}
