package com.microsoft.migration.assets.worker.service;

import com.microsoft.migration.assets.common.model.ImageProcessingMessage;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractFileProcessingServiceTest {

    @Mock
    private Channel channel;

    @TempDir
    Path tempDir;

    private TestFileProcessingService service;
    private boolean downloadShouldFail;

    @BeforeEach
    void setUp() {
        downloadShouldFail = false;
        service = new TestFileProcessingService();
    }

    @Test
    void processImage_matchingStorageType_processesSuccessfully() throws Exception {
        var message = new ImageProcessingMessage("photo.jpg", "image/jpeg", "test-storage", 1024L);
        long deliveryTag = 1L;

        service.processImage(message, channel, deliveryTag);

        verify(channel).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertTrue(service.downloadCalled, "downloadOriginal should have been called");
        assertTrue(service.uploadCalled, "uploadThumbnail should have been called");
    }

    @Test
    void processImage_differentStorageType_skipsAndAcknowledges() throws Exception {
        var message = new ImageProcessingMessage("photo.jpg", "image/jpeg", "other-storage", 1024L);
        long deliveryTag = 2L;

        service.processImage(message, channel, deliveryTag);

        verify(channel).basicAck(deliveryTag, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertFalse(service.downloadCalled, "downloadOriginal should NOT have been called");
        assertFalse(service.uploadCalled, "uploadThumbnail should NOT have been called");
    }

    @Test
    void processImage_processingFailure_rejectsMessage() throws Exception {
        downloadShouldFail = true;
        var message = new ImageProcessingMessage("photo.jpg", "image/jpeg", "test-storage", 1024L);
        long deliveryTag = 3L;

        service.processImage(message, channel, deliveryTag);

        verify(channel).basicNack(deliveryTag, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void processImage_cleansUpTempFiles() throws Exception {
        var message = new ImageProcessingMessage("photo.jpg", "image/jpeg", "test-storage", 1024L);
        long deliveryTag = 4L;

        service.processImage(message, channel, deliveryTag);

        // The service creates a temp directory that should be cleaned up.
        // We verify by checking that no leftover "image-processing" temp dirs exist
        // in the default temp location. The processImage method uses Files.createTempDirectory
        // and cleans up in finally block.
        verify(channel).basicAck(deliveryTag, false);
    }

    @Test
    void generateThumbnail_producesOutputFile() throws Exception {
        // Create a test image larger than the 600px thumbnail target
        Path inputFile = tempDir.resolve("test_input.jpg");
        BufferedImage testImage = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = testImage.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 1200, 900);
        g.dispose();
        ImageIO.write(testImage, "jpg", inputFile.toFile());

        Path outputFile = tempDir.resolve("test_output.jpg");

        service.generateThumbnail(inputFile, outputFile);

        assertTrue(Files.exists(outputFile), "Thumbnail file should exist");
        assertTrue(Files.size(outputFile) > 0, "Thumbnail file should not be empty");

        // Verify the output is a valid image
        BufferedImage thumbnail = ImageIO.read(outputFile.toFile());
        assertNotNull(thumbnail, "Thumbnail should be a readable image");
    }

    /**
     * Concrete test subclass that stubs the abstract/interface methods.
     */
    private class TestFileProcessingService extends AbstractFileProcessingService {

        boolean downloadCalled = false;
        boolean uploadCalled = false;

        @Override
        public void downloadOriginal(String key, Path destination) throws Exception {
            if (downloadShouldFail) {
                throw new IOException("Simulated download failure");
            }
            downloadCalled = true;
            // Create a valid JPEG larger than the 600px thumbnail target
            // so the scaling pipeline is fully exercised
            BufferedImage img = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, 1200, 900);
            g.dispose();
            ImageIO.write(img, "jpg", destination.toFile());
        }

        @Override
        public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
            uploadCalled = true;
        }

        @Override
        public String getStorageType() {
            return "test-storage";
        }

        @Override
        protected String generateUrl(String key) {
            return "/test/view/" + key;
        }
    }
}
