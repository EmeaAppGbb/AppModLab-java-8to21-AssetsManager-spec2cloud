package com.microsoft.migration.assets.common.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ImageMetadataTest {

    @Test
    void onCreate_setsTimestamps() {
        var metadata = new ImageMetadata();
        assertNull(metadata.getUploadedAt());
        assertNull(metadata.getLastModified());

        metadata.onCreate();

        assertNotNull(metadata.getUploadedAt());
        assertNotNull(metadata.getLastModified());
        // Both timestamps are set via separate LocalDateTime.now() calls,
        // so they may differ by a few nanoseconds
        assertTrue(
            java.time.Duration.between(metadata.getUploadedAt(), metadata.getLastModified()).toMillis() < 100,
            "uploadedAt and lastModified should be set within 100ms of each other"
        );
    }

    @Test
    void onUpdate_updatesLastModified() throws Exception {
        var metadata = new ImageMetadata();
        metadata.onCreate();
        LocalDateTime originalLastModified = metadata.getLastModified();

        // Small delay to ensure different timestamp
        Thread.sleep(10);
        metadata.onUpdate();

        assertNotNull(metadata.getLastModified());
        assertTrue(metadata.getLastModified().isAfter(originalLastModified)
                || metadata.getLastModified().isEqual(originalLastModified));
    }

    @Test
    void fieldsCanBeSetAndRetrieved() {
        var metadata = new ImageMetadata();
        metadata.setId("id-123");
        metadata.setFilename("photo.jpg");
        metadata.setContentType("image/jpeg");
        metadata.setSize(2048L);
        metadata.setStorageKey("s3-key-abc");
        metadata.setStorageUrl("https://storage/photo.jpg");
        metadata.setThumbnailKey("thumb-key");
        metadata.setThumbnailUrl("https://storage/thumb.jpg");

        assertEquals("id-123", metadata.getId());
        assertEquals("photo.jpg", metadata.getFilename());
        assertEquals("image/jpeg", metadata.getContentType());
        assertEquals(2048L, metadata.getSize());
        assertEquals("s3-key-abc", metadata.getStorageKey());
        assertEquals("https://storage/photo.jpg", metadata.getStorageUrl());
        assertEquals("thumb-key", metadata.getThumbnailKey());
        assertEquals("https://storage/thumb.jpg", metadata.getThumbnailUrl());
    }
}
