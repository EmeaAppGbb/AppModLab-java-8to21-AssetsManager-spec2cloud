package com.microsoft.migration.assets.worker.service;

import com.microsoft.migration.assets.common.repository.ImageMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CloudFileProcessingServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private ImageMetadataRepository imageMetadataRepository;

    @InjectMocks
    private CloudFileProcessingService cloudFileProcessingService;

    private final String bucketName = "test-bucket";
    private final String testKey = "test-image.jpg";
    private final String thumbnailKey = "test-image_thumbnail.jpg";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cloudFileProcessingService, "bucketName", bucketName);
    }

    @Test
    void getStorageTypeReturnsS3() {
        // Act
        String result = cloudFileProcessingService.getStorageType();

        // Assert
        assertEquals("cloud", result);
    }

    @Test
    void downloadOriginalCopiesFileFromS3() throws Exception {
        // Arrange
        Path tempFile = Files.createTempFile("download-", ".tmp");
        @SuppressWarnings("unchecked")
        ResponseInputStream<GetObjectResponse> mockResponse = mock(ResponseInputStream.class);

        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(mockResponse);

        // Act
        cloudFileProcessingService.downloadOriginal(testKey, tempFile);

        // Assert
        verify(s3Client).getObject(any(GetObjectRequest.class));

        // Clean up
        Files.deleteIfExists(tempFile);
    }

    @Test
    void uploadThumbnailPutsFileToS3() throws Exception {
        // Arrange
        Path tempFile = Files.createTempFile("thumbnail-", ".tmp");
        when(imageMetadataRepository.findByStorageKey(anyString())).thenReturn(Optional.empty());

        // Act
        cloudFileProcessingService.uploadThumbnail(tempFile, thumbnailKey, "image/jpeg");

        // Assert
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

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
