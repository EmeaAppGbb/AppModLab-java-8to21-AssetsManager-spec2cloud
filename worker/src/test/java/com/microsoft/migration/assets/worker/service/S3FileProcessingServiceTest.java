package com.microsoft.migration.assets.worker.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.migration.assets.worker.repository.ImageMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class S3FileProcessingServiceTest {

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private ImageMetadataRepository imageMetadataRepository;

    @Mock
    private BlobClient blobClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private S3FileProcessingService s3FileProcessingService;

    private final String testKey = "test-image.jpg";
    private final String thumbnailKey = "test-image_thumbnail.jpg";

    @BeforeEach
    void setUp() {
        s3FileProcessingService = new S3FileProcessingService(objectMapper, blobContainerClient, imageMetadataRepository);
    }

    @Test
    void getStorageTypeReturnsAzure() {
        var result = s3FileProcessingService.getStorageType();
        assertEquals("azure", result);
    }

    @Test
    void downloadOriginalCopiesFileFromBlob() throws Exception {
        var tempFile = Files.createTempFile("download-", ".tmp");

        when(blobContainerClient.getBlobClient(testKey)).thenReturn(blobClient);
        doNothing().when(blobClient).downloadStream(any(OutputStream.class));

        s3FileProcessingService.downloadOriginal(testKey, tempFile);

        verify(blobContainerClient).getBlobClient(testKey);
        verify(blobClient).downloadStream(any(OutputStream.class));

        Files.deleteIfExists(tempFile);
    }

    @Test
    void uploadThumbnailPutsFileToBlob() throws Exception {
        var tempFile = Files.createTempFile("thumbnail-", ".tmp");
        // Write some content so uploadFromFile works
        Files.writeString(tempFile, "test content");
        
        when(blobContainerClient.getBlobClient(thumbnailKey)).thenReturn(blobClient);
        doNothing().when(blobClient).uploadFromFile(anyString(), eq(true));
        when(imageMetadataRepository.findAll()).thenReturn(Collections.emptyList());

        s3FileProcessingService.uploadThumbnail(tempFile, thumbnailKey, "image/jpeg");

        verify(blobContainerClient).getBlobClient(thumbnailKey);
        verify(blobClient).uploadFromFile(anyString(), eq(true));

        Files.deleteIfExists(tempFile);
    }

    @Test
    void testExtractOriginalKey() {
        var result = (String) ReflectionTestUtils.invokeMethod(
                s3FileProcessingService,
                "extractOriginalKey",
                "image_thumbnail.jpg");

        assertEquals("image.jpg", result);
    }
}
