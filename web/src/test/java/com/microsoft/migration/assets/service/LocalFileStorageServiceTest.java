package com.microsoft.migration.assets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.microsoft.migration.assets.config.RabbitConfig.IMAGE_PROCESSING_QUEUE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.microsoft.migration.assets.common.model.ImageProcessingMessage;

@ExtendWith(MockitoExtension.class)
class LocalFileStorageServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @TempDir
    Path tempDir;

    private LocalFileStorageService service;

    @BeforeEach
    void setUp() throws IOException {
        service = new LocalFileStorageService(rabbitTemplate);
        ReflectionTestUtils.setField(service, "storageDirectory", tempDir.toString());
        service.init();
    }

    @Test
    void uploadObject_savesFileToDisk() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "image data".getBytes());

        service.uploadObject(file);

        assertTrue(Files.exists(tempDir.resolve("test.jpg")));
        assertEquals("image data", Files.readString(tempDir.resolve("test.jpg")));
        verify(rabbitTemplate).convertAndSend(eq(IMAGE_PROCESSING_QUEUE), any(ImageProcessingMessage.class));
    }

    @Test
    void uploadObject_emptyFile_throwsException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        IOException exception = assertThrows(IOException.class,
                () -> service.uploadObject(emptyFile));
        assertEquals("File is empty", exception.getMessage());
    }

    @Test
    void getObject_returnsInputStream() throws IOException {
        Files.writeString(tempDir.resolve("existing.jpg"), "file content");

        try (InputStream is = service.getObject("existing.jpg")) {
            assertNotNull(is);
            String content = new String(is.readAllBytes());
            assertEquals("file content", content);
        }
    }

    @Test
    void getObject_missingFile_throwsException() {
        assertThrows(FileNotFoundException.class,
                () -> service.getObject("nonexistent.jpg"));
    }

    @Test
    void deleteObject_removesFile() throws IOException {
        Path file = tempDir.resolve("to-delete.jpg");
        Files.writeString(file, "delete me");
        assertTrue(Files.exists(file));

        service.deleteObject("to-delete.jpg");

        assertFalse(Files.exists(file));
    }

    @Test
    void getStorageType_returnsLocal() {
        assertEquals("local", service.getStorageType());
    }
}
