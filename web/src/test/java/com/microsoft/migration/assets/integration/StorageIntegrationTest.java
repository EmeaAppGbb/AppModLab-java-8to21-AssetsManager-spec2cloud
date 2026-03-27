package com.microsoft.migration.assets.integration;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.microsoft.migration.assets.common.model.ImageMetadata;
import com.microsoft.migration.assets.common.repository.ImageMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "testuser", roles = "ADMIN")
class StorageIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("assets_manager")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management");

    @MockitoBean
    private BlobServiceClient blobServiceClient;

    @MockitoBean
    private BlobContainerClient blobContainerClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ImageMetadataRepository imageMetadataRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("azure.storage.connection-string", () -> "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=key;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1");
        registry.add("azure.storage.container-name", () -> "test-container");
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        PagedIterable<BlobItem> pagedIterable = mock(PagedIterable.class);
        when(pagedIterable.stream()).thenReturn(Stream.empty());
        when(blobContainerClient.listBlobs()).thenReturn(pagedIterable);
    }

    @Test
    void contextLoads() {
        assertThat(imageMetadataRepository).isNotNull();
    }

    @Test
    void homePage_redirectsToStorage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/storage"));
    }

    @Test
    void storageList_returnsOk() throws Exception {
        mockMvc.perform(get("/storage"))
                .andExpect(status().isOk())
                .andExpect(view().name("list"));
    }

    @Test
    void metadataRepository_crudOperations() {
        ImageMetadata metadata = new ImageMetadata();
        metadata.setId("test-id");
        metadata.setFilename("test.jpg");
        metadata.setContentType("image/jpeg");
        metadata.setSize(1024L);
        metadata.setStorageKey("test-key");
        metadata.setStorageUrl("http://test/url");

        imageMetadataRepository.save(metadata);

        assertThat(imageMetadataRepository.findById("test-id")).isPresent();
        assertThat(imageMetadataRepository.findByStorageKey("test-key")).isPresent();

        imageMetadataRepository.deleteById("test-id");
        assertThat(imageMetadataRepository.findById("test-id")).isEmpty();
    }

    @Test
    void uploadForm_returnsOk() throws Exception {
        mockMvc.perform(get("/storage/upload"))
                .andExpect(status().isOk())
                .andExpect(view().name("upload"));
    }
}
