package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.model.StorageItem;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageServiceDefaultMethodsTest {

    /**
     * Minimal implementation to test the default methods on the StorageService interface.
     */
    private final StorageService service = new StorageService() {
        @Override
        public List<StorageItem> listObjects() { return List.of(); }

        @Override
        public void uploadObject(MultipartFile file) {}

        @Override
        public InputStream getObject(String key) { return InputStream.nullInputStream(); }

        @Override
        public void deleteObject(String key) {}

        @Override
        public String getStorageType() { return "test"; }
    };

    @Test
    void getThumbnailKey_withExtension() {
        assertEquals("image_thumbnail.jpg", service.getThumbnailKey("image.jpg"));
    }

    @Test
    void getThumbnailKey_withoutExtension() {
        assertEquals("image_thumbnail", service.getThumbnailKey("image"));
    }

    @Test
    void generateUrl_returnsCorrectPath() {
        assertEquals("/storage/view/mykey", service.generateUrl("mykey"));
    }
}
