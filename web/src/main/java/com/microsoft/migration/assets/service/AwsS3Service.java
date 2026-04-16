package com.microsoft.migration.assets.service;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.migration.assets.model.ImageMetadata;
import com.microsoft.migration.assets.model.ImageProcessingMessage;
import com.microsoft.migration.assets.model.S3StorageItem;
import com.microsoft.migration.assets.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.microsoft.migration.assets.config.RabbitConfig.IMAGE_PROCESSING_QUEUE;

@Service
@RequiredArgsConstructor
@Profile("!dev")
public final class AwsS3Service implements StorageService {

    private final BlobContainerClient blobContainerClient;
    private final ServiceBusSenderClient serviceBusSenderClient;
    private final ImageMetadataRepository imageMetadataRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<S3StorageItem> listObjects() {
        return blobContainerClient.listBlobs().stream()
                .map(blobItem -> {
                    var uploadedAt = imageMetadataRepository.findAll().stream()
                            .filter(metadata -> metadata.getS3Key().equals(blobItem.getName()))
                            .map(metadata -> metadata.getUploadedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                            .findFirst()
                            .orElse(blobItem.getProperties().getLastModified().toInstant());

                    return new S3StorageItem(
                            blobItem.getName(),
                            extractFilename(blobItem.getName()),
                            blobItem.getProperties().getContentLength(),
                            blobItem.getProperties().getLastModified().toInstant(),
                            uploadedAt,
                            generateUrl(blobItem.getName())
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        var key = generateKey(file.getOriginalFilename());
        var blobClient = blobContainerClient.getBlobClient(key);
        
        blobClient.upload(file.getInputStream(), file.getSize(), true);
        blobClient.setHttpHeaders(new com.azure.storage.blob.models.BlobHttpHeaders()
                .setContentType(file.getContentType()));

        // Send message to Service Bus for thumbnail generation
        var message = new ImageProcessingMessage(
            key,
            file.getContentType(),
            getStorageType(),
            file.getSize()
        );
        try {
            var json = objectMapper.writeValueAsString(message);
            serviceBusSenderClient.sendMessage(new ServiceBusMessage(json));
        } catch (Exception e) {
            throw new IOException("Failed to send message to Service Bus", e);
        }

        // Create and save metadata to database
        var metadata = new ImageMetadata();
        metadata.setId(UUID.randomUUID().toString());
        metadata.setFilename(file.getOriginalFilename());
        metadata.setContentType(file.getContentType());
        metadata.setSize(file.getSize());
        metadata.setS3Key(key);
        metadata.setS3Url(generateUrl(key));

        imageMetadataRepository.save(metadata);
    }

    @Override
    public InputStream getObject(String key) throws IOException {
        var blobClient = blobContainerClient.getBlobClient(key);
        return blobClient.openInputStream();
    }

    @Override
    public void deleteObject(String key) throws IOException {
        var blobClient = blobContainerClient.getBlobClient(key);
        blobClient.deleteIfExists();

        try {
            var thumbnailClient = blobContainerClient.getBlobClient(getThumbnailKey(key));
            thumbnailClient.deleteIfExists();
        } catch (Exception e) {
            // Ignore if thumbnail doesn't exist
        }

        // Delete metadata from database
        imageMetadataRepository.findAll().stream()
                .filter(metadata -> metadata.getS3Key().equals(key))
                .findFirst()
                .ifPresent(imageMetadataRepository::delete);
    }

    @Override
    public String getStorageType() {
        return "azure";
    }

    private String extractFilename(String key) {
        var lastSlashIndex = key.lastIndexOf('/');
        return lastSlashIndex >= 0 ? key.substring(lastSlashIndex + 1) : key;
    }

    private String generateKey(String filename) {
        return UUID.randomUUID().toString() + "-" + filename;
    }
}