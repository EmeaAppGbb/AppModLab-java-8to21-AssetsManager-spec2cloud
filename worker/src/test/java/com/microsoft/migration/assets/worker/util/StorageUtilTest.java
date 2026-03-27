package com.microsoft.migration.assets.worker.util;

import com.microsoft.migration.assets.common.util.StorageUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StorageUtilTest {

    @Test
    void getThumbnailKey_withExtension() {
        assertEquals("image_thumbnail.jpg", StorageUtil.getThumbnailKey("image.jpg"));
    }

    @Test
    void getThumbnailKey_withoutExtension() {
        assertEquals("image_thumbnail", StorageUtil.getThumbnailKey("image"));
    }

    @Test
    void getExtension_withDot() {
        assertEquals(".jpg", StorageUtil.getExtension("file.jpg"));
    }

    @Test
    void getExtension_noDot() {
        assertEquals("", StorageUtil.getExtension("file"));
    }
}
