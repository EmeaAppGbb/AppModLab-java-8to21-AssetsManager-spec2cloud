package com.microsoft.migration.assets.controller;

import com.microsoft.migration.assets.model.StorageItem;
import com.microsoft.migration.assets.service.FolderService;
import com.microsoft.migration.assets.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class StorageControllerTest {

    @Mock
    private StorageService storageService;

    @Mock
    private FolderService folderService;

    @InjectMocks
    private StorageController storageController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");
        mockMvc = MockMvcBuilders.standaloneSetup(storageController)
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void listObjects_returnsListView() throws Exception {
        StorageItem item = new StorageItem("key1", "image.jpg", 1024L,
                Instant.now(), Instant.now(), "/storage/view/key1", null);
        when(storageService.listObjects()).thenReturn(List.of(item));

        mockMvc.perform(get("/storage"))
                .andExpect(status().isOk())
                .andExpect(view().name("list"))
                .andExpect(model().attributeExists("objects"));

        verify(storageService).listObjects();
    }

    @Test
    void uploadForm_returnsUploadView() throws Exception {
        mockMvc.perform(get("/storage/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("upload"));
    }

    @Test
    void uploadObject_success_redirectsToStorage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "test content".getBytes());

        mockMvc.perform(multipart("/storage/upload").file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/storage"))
                .andExpect(flash().attribute("success", "File uploaded successfully"));

        verify(storageService).uploadObject(file, null);
    }

    @Test
    void uploadObject_emptyFile_redirectsWithError() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/storage/upload").file(emptyFile))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/storage/upload"))
                .andExpect(flash().attribute("error", "Please select a file to upload"));

        verify(storageService, never()).uploadObject(any(), any());
    }

    @Test
    void viewObject_found_returnsStream() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("image data".getBytes());
        when(storageService.getObject("test-key")).thenReturn(inputStream);

        mockMvc.perform(get("/storage/view/{key}", "test-key"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"));

        verify(storageService).getObject("test-key");
    }

    @Test
    void viewObject_notFound_returns404() throws Exception {
        when(storageService.getObject("missing-key")).thenThrow(new IOException("Not found"));

        mockMvc.perform(get("/storage/view/{key}", "missing-key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewObject_invalidKey_returns400() throws Exception {
        mockMvc.perform(get("/storage/view/{key}", "..secret"))
                .andExpect(status().isBadRequest());

        verify(storageService, never()).getObject(anyString());
    }

    @Test
    void deleteObject_success_redirectsWithMessage() throws Exception {
        mockMvc.perform(post("/storage/delete/{key}", "test-key"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/storage"))
                .andExpect(flash().attribute("success", "File deleted successfully"));

        verify(storageService).deleteObject("test-key");
    }
}
