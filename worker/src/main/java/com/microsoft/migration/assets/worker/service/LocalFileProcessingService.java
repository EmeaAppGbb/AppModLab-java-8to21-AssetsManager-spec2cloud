package com.microsoft.migration.assets.worker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@Profile("dev")
public class LocalFileProcessingService extends AbstractFileProcessingService {
    
    @Value("${local.storage.directory:../storage}")
    private String storageDirectory;
    
    private Path rootLocation;
    
    @PostConstruct
    public void init() throws Exception {
        rootLocation = Paths.get(storageDirectory).toAbsolutePath().normalize();
        log.info("Local storage directory: {}", rootLocation);
        
        if (!Files.exists(rootLocation)) {
            Files.createDirectories(rootLocation);
            log.info("Created local storage directory");
        }
    }

    @Override
    public void downloadOriginal(String key, Path destination) throws Exception {
        Path sourcePath = rootLocation.resolve(key);
        if (!Files.exists(sourcePath)) {
            throw new java.io.FileNotFoundException("File not found: " + sourcePath);
        }
        Files.copy(sourcePath, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void uploadThumbnail(Path source, String key, String contentType) throws Exception {
        Path destinationPath = rootLocation.resolve(key);
        Files.createDirectories(destinationPath.getParent());
        Files.copy(source, destinationPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public String getStorageType() {
        return "local";
    }

    @Override
    protected String generateUrl(String key) {
        // For local storage, we'll just return the relative path
        return "/storage/" + key;
    }
}