package com.microsoft.migration.assets.worker.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.migration.assets.worker.repository.ImageMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Profile("!dev")
public class S3FileProcessingService extends AbstractFileProcessingService {
    private final BlobContainerClient blobContainerClient;
    private final ImageMetadataRepository imageMetadataRepository;

    public S3FileProcessingService(ObjectMapper objectMapper, 
                                    BlobContainerClient blobContainerClient,
                                    ImageMetadataRepository imageMetadataRepository) {
        super(objectMapper);
        this.blobContainerClient = blobContainerClient;
        this.imageMetadataRepository = imageMetadataRepository;
    }

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        var blobClient = blobContainerClient.getBlobClient(key);
        try (var outputStream = Files.newOutputStream(destination)) {
            blobClient.downloadStream(outputStream);
        }
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        var blobClient = blobContainerClient.getBlobClient(key);
        blobClient.uploadFromFile(source.toString(), true);
        blobClient.setHttpHeaders(new com.azure.storage.blob.models.BlobHttpHeaders()
                .setContentType(contentType));

        // Extract the original key from the thumbnail key
        var originalKey = extractOriginalKey(key);
        
        // Find and update metadata
        imageMetadataRepository.findAll().stream()
            .filter(metadata -> metadata.getS3Key().equals(originalKey))
            .findFirst()
            .ifPresent(metadata -> {
                metadata.setThumbnailKey(key);
                metadata.setThumbnailUrl(generateUrl(key));
                imageMetadataRepository.save(metadata);
            });
    }

    @Override
    public String getStorageType() {
        return "azure";
    }

    @Override
    protected String generateUrl(String key) {
        return blobContainerClient.getBlobClient(key).getBlobUrl();
    }

    private String extractOriginalKey(String key) {
        var suffix = "_thumbnail";
        var extensionIndex = key.lastIndexOf('.');
        if (extensionIndex > 0) {
            var nameWithoutExtension = key.substring(0, extensionIndex);
            var extension = key.substring(extensionIndex);
            
            var suffixIndex = nameWithoutExtension.lastIndexOf(suffix);
            if (suffixIndex > 0) {
                return nameWithoutExtension.substring(0, suffixIndex) + extension;
            }
        }
        return key;
    }
}