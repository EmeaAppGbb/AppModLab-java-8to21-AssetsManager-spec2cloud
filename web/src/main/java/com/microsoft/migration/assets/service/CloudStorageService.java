package com.microsoft.migration.assets.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.microsoft.migration.assets.common.model.ImageMetadata;
import com.microsoft.migration.assets.common.model.ImageProcessingMessage;
import com.microsoft.migration.assets.model.StorageItem;
import com.microsoft.migration.assets.common.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.microsoft.migration.assets.config.RabbitConfig.IMAGE_PROCESSING_QUEUE;

@Service
@RequiredArgsConstructor
@Profile("!dev") // Active when not in dev profile
public class CloudStorageService implements StorageService {

    private final BlobContainerClient blobContainerClient;
    private final RabbitTemplate rabbitTemplate;
    private final ImageMetadataRepository imageMetadataRepository;

    @Override
    public List<StorageItem> listObjects() {
        Map<String, ImageMetadata> metadataMap = imageMetadataRepository.findAll().stream()
                .collect(Collectors.toMap(ImageMetadata::getStorageKey, m -> m, (a, b) -> a));

        return blobContainerClient.listBlobs().stream()
                .map(blobItem -> {
                    String key = blobItem.getName();
                    ImageMetadata meta = metadataMap.get(key);
                    Instant lastModified = blobItem.getProperties().getLastModified().toInstant();
                    Instant uploadedAt = meta != null
                            ? meta.getUploadedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
                            : lastModified;

                    return new StorageItem(
                            key,
                            extractFilename(key),
                            blobItem.getProperties().getContentLength(),
                            lastModified,
                            uploadedAt,
                            generateUrl(key)
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void uploadObject(MultipartFile file) throws IOException {
        validateImageFile(file);
        String key = generateKey(file.getOriginalFilename());
        BlobClient blobClient = blobContainerClient.getBlobClient(key);
        blobClient.upload(file.getInputStream(), file.getSize(), true);

        // Send message to queue for thumbnail generation
        ImageProcessingMessage message = new ImageProcessingMessage(
            key,
            file.getContentType(),
            getStorageType(),
            file.getSize()
        );
        rabbitTemplate.convertAndSend(IMAGE_PROCESSING_QUEUE, message);

        // Create and save metadata to database
        ImageMetadata metadata = new ImageMetadata();
        metadata.setId(UUID.randomUUID().toString());
        metadata.setFilename(file.getOriginalFilename());
        metadata.setContentType(file.getContentType());
        metadata.setSize(file.getSize());
        metadata.setStorageKey(key);
        metadata.setStorageUrl(generateUrl(key));

        imageMetadataRepository.save(metadata);
    }

    @Override
    public InputStream getObject(String key) throws IOException {
        BlobClient blobClient = blobContainerClient.getBlobClient(key);
        return blobClient.openInputStream();
    }

    @Override
    @Transactional
    public void deleteObject(String key) throws IOException {
        BlobClient blobClient = blobContainerClient.getBlobClient(key);
        blobClient.delete();

        try {
            // Try to delete thumbnail if it exists
            BlobClient thumbnailClient = blobContainerClient.getBlobClient(getThumbnailKey(key));
            thumbnailClient.delete();
        } catch (Exception e) {
            // Ignore if thumbnail doesn't exist
        }

        // Delete metadata from database
        imageMetadataRepository.findByStorageKey(key).ifPresent(imageMetadataRepository::delete);
    }

    @Override
    public String getStorageType() {
        return "cloud";
    }

    private String extractFilename(String key) {
        // Extract filename from the object key
        int lastSlashIndex = key.lastIndexOf('/');
        return lastSlashIndex >= 0 ? key.substring(lastSlashIndex + 1) : key;
    }

    private String generateKey(String filename) {
        return UUID.randomUUID().toString() + "-" + filename;
    }
}