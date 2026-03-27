package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.constants.StorageConstants;
import com.microsoft.migration.assets.common.model.ImageProcessingMessage;
import com.microsoft.migration.assets.model.StorageItem;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Interface for storage operations that can be implemented by different storage providers
 * (AWS S3, local file system, etc.)
 */
public interface StorageService {
    
    /**
     * List all objects in storage
     */
    List<StorageItem> listObjects();
    
    /**
     * Upload file to storage
     */
    void uploadObject(MultipartFile file) throws IOException;
    
    /**
     * Get object from storage by key
     */
    InputStream getObject(String key) throws IOException;

    /**
     * Delete object from storage by key
     */
    void deleteObject(String key) throws IOException;

    /**
     * Get the storage type (s3 or local)
     */
    String getStorageType();

    /**
     * Validate that the uploaded file is a supported image type.
     */
    default void validateImageFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("File is empty");
        }

        Set<String> allowedTypes = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
        String contentType = file.getContentType();
        if (contentType == null || !allowedTypes.contains(contentType.toLowerCase())) {
            throw new IOException("Invalid file type. Allowed: JPEG, PNG, GIF, WebP");
        }

        String filename = file.getOriginalFilename();
        if (filename != null) {
            Set<String> allowedExtensions = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");
            String extension = filename.lastIndexOf('.') > 0
                    ? filename.substring(filename.lastIndexOf('.')).toLowerCase() : "";
            if (!allowedExtensions.contains(extension)) {
                throw new IOException("Invalid file extension. Allowed: jpg, jpeg, png, gif, webp");
            }
        }
    }

    /**
     * Get the thumbnail key for a given key
     */
    default String getThumbnailKey(String key) {
        int dotIndex = key.lastIndexOf('.');
        if (dotIndex > 0) {
            return key.substring(0, dotIndex) + "_thumbnail" + key.substring(dotIndex);
        }
        return key + "_thumbnail";
    }

    /**
     * Generate a URL for viewing the object
     */
    default String generateUrl(String key) {
        return "/" + StorageConstants.STORAGE_PATH + "/view/" + key;
    }
}