package com.microsoft.migration.assets.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class LocalFileProcessingServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileProcessingService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new LocalFileProcessingService();
        ReflectionTestUtils.setField(service, "storageDirectory", tempDir.toString());
        service.init();
    }

    @Test
    void downloadOriginal_copiesFile() throws Exception {
        // Arrange — create a source file in the storage root
        Path sourceFile = tempDir.resolve("photo.jpg");
        Files.writeString(sourceFile, "fake-image-content");

        Path destination = tempDir.resolve("dest.jpg");

        // Act
        service.downloadOriginal("photo.jpg", destination);

        // Assert
        assertTrue(Files.exists(destination));
        assertEquals("fake-image-content", Files.readString(destination));
    }

    @Test
    void downloadOriginal_missingFile_throwsException() {
        Path destination = tempDir.resolve("dest.jpg");

        // Act & Assert
        assertThrows(FileNotFoundException.class,
                () -> service.downloadOriginal("nonexistent.jpg", destination));
    }

    @Test
    void uploadThumbnail_copiesFile() throws Exception {
        // Arrange — create a thumbnail source
        Path thumbnailSource = tempDir.resolve("thumb_src.jpg");
        Files.writeString(thumbnailSource, "thumbnail-bytes");

        // Act
        service.uploadThumbnail(thumbnailSource, "photo_thumbnail.jpg", "image/jpeg");

        // Assert
        Path expected = tempDir.resolve("photo_thumbnail.jpg");
        assertTrue(Files.exists(expected));
        assertEquals("thumbnail-bytes", Files.readString(expected));
    }

    @Test
    void getStorageType_returnsLocal() {
        assertEquals("local", service.getStorageType());
    }
}
