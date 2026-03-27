package com.microsoft.migration.assets.service;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobItemProperties;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.microsoft.migration.assets.common.model.ImageMetadata;
import com.microsoft.migration.assets.common.model.ImageProcessingMessage;
import com.microsoft.migration.assets.common.repository.ImageMetadataRepository;
import com.microsoft.migration.assets.model.StorageItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.microsoft.migration.assets.config.RabbitConfig.IMAGE_PROCESSING_QUEUE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudStorageServiceTest {

    @Mock
    private BlobContainerClient blobContainerClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ImageMetadataRepository imageMetadataRepository;

    @InjectMocks
    private CloudStorageService cloudStorageService;

    @Test
    @SuppressWarnings("unchecked")
    void listObjects_returnsMappedItems() {
        // Arrange: blob item
        BlobItemProperties props = new BlobItemProperties()
                .setLastModified(OffsetDateTime.now())
                .setContentLength(2048L);
        BlobItem blobItem = new BlobItem().setName("test-key.jpg").setProperties(props);

        PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
        when(blobContainerClient.listBlobs()).thenReturn(pagedIterable);
        when(pagedIterable.stream()).thenReturn(Stream.of(blobItem));

        // Arrange: metadata
        ImageMetadata metadata = new ImageMetadata();
        metadata.setStorageKey("test-key.jpg");
        metadata.setUploadedAt(LocalDateTime.now());
        when(imageMetadataRepository.findAll()).thenReturn(List.of(metadata));

        // Act
        List<StorageItem> result = cloudStorageService.listObjects();

        // Assert
        assertEquals(1, result.size());
        StorageItem item = result.get(0);
        assertEquals("test-key.jpg", item.key());
        assertEquals("test-key.jpg", item.name());
        assertEquals(2048L, item.size());
        assertNotNull(item.lastModified());
        assertNotNull(item.uploadedAt());
        assertTrue(item.url().contains("test-key.jpg"));
    }

    @Test
    void uploadObject_storesAndSendsMessage() throws IOException {
        // Arrange
        byte[] content = "image bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", content);

        BlobClient blobClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);

        // Act
        cloudStorageService.uploadObject(file);

        // Assert: blob upload
        verify(blobClient).upload(any(InputStream.class), eq((long) content.length), eq(true));

        // Assert: RabbitMQ message
        ArgumentCaptor<ImageProcessingMessage> msgCaptor = ArgumentCaptor.forClass(ImageProcessingMessage.class);
        verify(rabbitTemplate).convertAndSend(eq(IMAGE_PROCESSING_QUEUE), msgCaptor.capture());
        ImageProcessingMessage sentMessage = msgCaptor.getValue();
        assertEquals("image/jpeg", sentMessage.contentType());
        assertEquals("cloud", sentMessage.storageType());
        assertEquals(content.length, sentMessage.size());

        // Assert: metadata saved
        verify(imageMetadataRepository).save(any(ImageMetadata.class));
    }

    @Test
    void getObject_returnsInputStream() throws IOException {
        BlobClient blobClient = mock(BlobClient.class);
        BlobInputStream blobInputStream = mock(BlobInputStream.class);
        when(blobContainerClient.getBlobClient("test-key")).thenReturn(blobClient);
        when(blobClient.openInputStream()).thenReturn(blobInputStream);

        InputStream result = cloudStorageService.getObject("test-key");

        assertSame(blobInputStream, result);
        verify(blobClient).openInputStream();
    }

    @Test
    void deleteObject_deletesBlobAndMetadata() throws IOException {
        // Arrange
        BlobClient blobClient = mock(BlobClient.class);
        BlobClient thumbnailClient = mock(BlobClient.class);
        when(blobContainerClient.getBlobClient("photo.jpg")).thenReturn(blobClient);
        when(blobContainerClient.getBlobClient("photo_thumbnail.jpg")).thenReturn(thumbnailClient);

        ImageMetadata metadata = new ImageMetadata();
        metadata.setStorageKey("photo.jpg");
        when(imageMetadataRepository.findByStorageKey("photo.jpg")).thenReturn(Optional.of(metadata));

        // Act
        cloudStorageService.deleteObject("photo.jpg");

        // Assert
        verify(blobClient).delete();
        verify(thumbnailClient).delete();
        verify(imageMetadataRepository).delete(metadata);
    }

    @Test
    void getStorageType_returnsCloud() {
        assertEquals("cloud", cloudStorageService.getStorageType());
    }
}
