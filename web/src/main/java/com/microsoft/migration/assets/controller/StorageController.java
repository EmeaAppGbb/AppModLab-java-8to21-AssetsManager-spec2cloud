package com.microsoft.migration.assets.controller;

import com.microsoft.migration.assets.constants.StorageConstants;
import com.microsoft.migration.assets.model.StorageItem;
import com.microsoft.migration.assets.service.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/" + StorageConstants.STORAGE_PATH)
@RequiredArgsConstructor
@Tag(name = "Storage", description = "Image storage operations")
public class StorageController {

    private final StorageService storageService;

    @GetMapping
    @Operation(summary = "List all images")
    public String listObjects(Model model) {
        List<StorageItem> objects = storageService.listObjects();
        model.addAttribute("objects", objects);
        return "list";
    }

    @GetMapping("/upload")
    @Operation(summary = "Show upload form")
    public String uploadForm() {
        return "upload";
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload an image")
    public String uploadObject(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
                return "redirect:/" + StorageConstants.STORAGE_PATH + "/upload";
            }

            storageService.uploadObject(file);
            redirectAttributes.addFlashAttribute("success", "File uploaded successfully");
            return "redirect:/" + StorageConstants.STORAGE_PATH;
        } catch (IOException e) {
            log.error("File upload failed", e);
            redirectAttributes.addFlashAttribute("error", "Failed to upload file. Please try again.");
            return "redirect:/" + StorageConstants.STORAGE_PATH + "/upload";
        }
    }
    
    @GetMapping("/view-page/{key}")
    @Operation(summary = "View image details page")
    public String viewObjectPage(@PathVariable String key, Model model, RedirectAttributes redirectAttributes) {
        validateKey(key);
        try {
            // Find the object in the list of objects
            Optional<StorageItem> foundObject = storageService.listObjects().stream()
                    .filter(obj -> obj.key().equals(key))
                    .findFirst();
            
            if (foundObject.isPresent()) {
                model.addAttribute("object", foundObject.get());
                return "view";
            } else {
                redirectAttributes.addFlashAttribute("error", "Image not found");
                return "redirect:/" + StorageConstants.STORAGE_PATH;
            }
        } catch (Exception e) {
            log.error("Failed to view image", e);
            redirectAttributes.addFlashAttribute("error", "Failed to view image. Please try again.");
            return "redirect:/" + StorageConstants.STORAGE_PATH;
        }
    }

    @GetMapping("/view/{key}")
    @Operation(summary = "Download image content")
    public ResponseEntity<InputStreamResource> viewObject(@PathVariable String key) {
        try {
            validateKey(key);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        try {
            InputStream inputStream = storageService.getObject(key);
            
            HttpHeaders headers = new HttpHeaders();
            // Use a generic content type if we don't know the exact type
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(inputStream));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/delete/{key}")
    @Operation(summary = "Delete an image")
    public String deleteObject(@PathVariable String key, RedirectAttributes redirectAttributes) {
        validateKey(key);
        try {
            storageService.deleteObject(key);
            redirectAttributes.addFlashAttribute("success", "File deleted successfully");
        } catch (Exception e) {
            log.error("File deletion failed", e);
            redirectAttributes.addFlashAttribute("error", "Failed to delete file. Please try again.");
        }
        return "redirect:/" + StorageConstants.STORAGE_PATH;
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be empty");
        }
        // Decode URL-encoded characters before validation
        String decoded = java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8);
        if (decoded.contains("..") || decoded.contains("/") || decoded.contains("\\")) {
            throw new IllegalArgumentException("Storage key contains invalid characters");
        }
        // Reject control characters and null bytes
        if (decoded.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
            throw new IllegalArgumentException("Storage key contains invalid characters");
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleInvalidKey(IllegalArgumentException e, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        return "redirect:/" + StorageConstants.STORAGE_PATH;
    }
}