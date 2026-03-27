package com.microsoft.migration.assets.common.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageProcessingMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordAccessors_returnCorrectValues() {
        var message = new ImageProcessingMessage("photo.jpg", "image/jpeg", "local", 4096L);

        assertEquals("photo.jpg", message.key());
        assertEquals("image/jpeg", message.contentType());
        assertEquals("local", message.storageType());
        assertEquals(4096L, message.size());
    }

    @Test
    void jsonSerialization_roundTrip() throws Exception {
        var original = new ImageProcessingMessage("test.png", "image/png", "cloud", 2048L);

        String json = objectMapper.writeValueAsString(original);
        var deserialized = objectMapper.readValue(json, ImageProcessingMessage.class);

        assertEquals(original.key(), deserialized.key());
        assertEquals(original.contentType(), deserialized.contentType());
        assertEquals(original.storageType(), deserialized.storageType());
        assertEquals(original.size(), deserialized.size());
    }

    @Test
    void equality_sameValues() {
        var msg1 = new ImageProcessingMessage("file.jpg", "image/jpeg", "local", 1024L);
        var msg2 = new ImageProcessingMessage("file.jpg", "image/jpeg", "local", 1024L);

        assertEquals(msg1, msg2);
        assertEquals(msg1.hashCode(), msg2.hashCode());
    }

    @Test
    void equality_differentValues() {
        var msg1 = new ImageProcessingMessage("file1.jpg", "image/jpeg", "local", 1024L);
        var msg2 = new ImageProcessingMessage("file2.jpg", "image/png", "cloud", 2048L);

        assertNotEquals(msg1, msg2);
    }
}
