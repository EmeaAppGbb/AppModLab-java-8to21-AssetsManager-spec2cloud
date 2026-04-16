package com.microsoft.migration.assets.service;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.migration.assets.model.ImageProcessingMessage;
import com.microsoft.migration.assets.model.S3StorageItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Profile("dev") // Only active when dev profile is active
public final class LocalFileStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileStorageService.class);
    
    private final ServiceBusSenderClient serviceBusSenderClient;
    private final ObjectMapper objectMapper;
    
    @Value("${local.storage.directory:../storage}")
    private String storageDirectory;
    
    private Path rootLocation;

    public LocalFileStorageService(ServiceBusSenderClient serviceBusSenderClient, ObjectMapper objectMapper) {
        this.serviceBusSenderClient = serviceBusSenderClient;
        this.objectMapper = objectMapper;
    }
    
    @PostConstruct
    public void init() throws IOException {
        rootLocation = Paths.get(storageDirectory).toAbsolutePath().normalize();
        logger.info("Local storage directory: {}", rootLocation);
        
        // Create directory if it doesn't exist
        if (!Files.exists(rootLocation)) {
            Files.createDirectories(rootLocation);
            logger.info("Created local storage directory");
        }
    }

    @Override
    public List<S3StorageItem> listObjects() {
        try {
            return Files.walk(rootLocation, 1)
                .filter(path -> !path.equals(rootLocation))
                .map(path -> {
                    try {
                        var filename = path.getFileName().toString();
                        var attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        return new S3StorageItem(
                            filename,
                            filename,
                            Files.size(path),
                            attrs.lastModifiedTime().toInstant(),
                            attrs.creationTime().toInstant(),
                            generateUrl(filename)
                        );
                    } catch (IOException e) {
                        logger.error("Failed to read file attributes", e);
                        return null;
                    }
                })
                .filter(item -> item != null)
                .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Failed to list files", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void uploadObject(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }
        
        var filename = StringUtils.cleanPath(file.getOriginalFilename());
        if (filename.contains("..")) {
            throw new IOException("Cannot store file with relative path outside current directory");
        }
        
        var targetLocation = rootLocation.resolve(filename);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Stored file: {}", targetLocation);

        // Send message to Service Bus for thumbnail generation
        var message = new ImageProcessingMessage(
            filename,
            file.getContentType(),
            getStorageType(),
            file.getSize()
        );
        try {
            var json = objectMapper.writeValueAsString(message);
            serviceBusSenderClient.sendMessage(new ServiceBusMessage(json));
        } catch (Exception e) {
            logger.error("Failed to send message to Service Bus", e);
        }
    }

    @Override
    public InputStream getObject(String key) throws IOException {
        var file = rootLocation.resolve(key);
        if (!Files.exists(file)) {
            throw new FileNotFoundException("File not found: " + key);
        }
        return new BufferedInputStream(Files.newInputStream(file));
    }

    @Override
    public void deleteObject(String key) throws IOException {
        var file = rootLocation.resolve(key);
        if (!Files.exists(file)) {
            throw new FileNotFoundException("File not found: " + key);
        }
        Files.delete(file);
        logger.info("Deleted file: {}", file);

        // Try to delete thumbnail if it exists
        try {
            var thumbnailFile = rootLocation.resolve(getThumbnailKey(key));
            if (Files.exists(thumbnailFile)) {
                Files.delete(thumbnailFile);
                logger.info("Deleted thumbnail file: {}", thumbnailFile);
            }
        } catch (Exception e) {
            logger.warn("Could not delete thumbnail for {}: {}", key, e.getMessage());
        }
    }

    @Override
    public String getStorageType() {
        return "local";
    }
}