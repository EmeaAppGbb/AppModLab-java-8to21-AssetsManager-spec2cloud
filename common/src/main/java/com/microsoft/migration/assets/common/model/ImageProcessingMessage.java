package com.microsoft.migration.assets.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageProcessingMessage {
    private String key;
    private String contentType;
    private String storageType; // "cloud" or "local"
    private long size;
}
