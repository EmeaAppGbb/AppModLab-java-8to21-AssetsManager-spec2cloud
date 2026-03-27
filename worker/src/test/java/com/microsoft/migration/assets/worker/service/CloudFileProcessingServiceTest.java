package com.microsoft.migration.assets.worker.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.microsoft.migration.assets.common.repository.ImageMetadataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CloudFileProcessingServiceTest {

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private ImageMetadataRepository imageMetadataRepository;

    @InjectMocks
    private CloudFileProcessingService cloudFileProcessingService;

    private final String testKey = "test-image.jpg";
    private final String thumbnailKey = "test-image_thumbnail.jpg";

    @Test
    void getStorageTypeReturnsCloud() {
        // Act
        String result = cloudFileProcessingService.getStorageType();

        // Assert
        assertEquals("cloud", result);
    }

    @Test
    void downloadOriginalDownloadsFromBlobStorage() throws Exception {
        // Arrange
        Path tempFile = Files.createTempFile("download-", ".tmp");
        BlobClient mockBlobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(testKey)).thenReturn(mockBlobClient);

        // Act
        cloudFileProcessingService.downloadOriginal(testKey, tempFile);

        // Assert
        verify(blobContainerClient).getBlobClient(testKey);
        verify(mockBlobClient).downloadToFile(tempFile.toString(), true);

        // Clean up
        Files.deleteIfExists(tempFile);
    }

    @Test
    void uploadThumbnailUploadsToBlobStorage() throws Exception {
        // Arrange
        Path tempFile = Files.createTempFile("thumbnail-", ".tmp");
        BlobClient mockBlobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(thumbnailKey)).thenReturn(mockBlobClient);
        when(imageMetadataRepository.findByStorageKey(anyString())).thenReturn(Optional.empty());

        // Act
        cloudFileProcessingService.uploadThumbnail(tempFile, thumbnailKey, "image/jpeg");

        // Assert
        verify(blobContainerClient).getBlobClient(thumbnailKey);
        verify(mockBlobClient).uploadFromFile(tempFile.toString(), true);

        // Clean up
        Files.deleteIfExists(tempFile);
    }

    @Test
    void testExtractOriginalKey() throws Exception {
        // Use reflection to test private method
        String result = (String) ReflectionTestUtils.invokeMethod(
                cloudFileProcessingService,
                "extractOriginalKey",
                "image_thumbnail.jpg");

        // Assert
        assertEquals("image.jpg", result);
    }
}
