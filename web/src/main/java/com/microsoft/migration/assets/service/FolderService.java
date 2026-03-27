package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.common.model.Folder;
import com.microsoft.migration.assets.common.model.ImageMetadata;
import com.microsoft.migration.assets.common.repository.FolderRepository;
import com.microsoft.migration.assets.common.repository.ImageMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final ImageMetadataRepository imageMetadataRepository;

    private static final int MAX_DEPTH = 5;

    public List<Folder> getFoldersAtLevel(Long parentId) {
        if (parentId == null) {
            return folderRepository.findByParentIdIsNull();
        }
        return folderRepository.findByParentId(parentId);
    }

    public Optional<Folder> getFolder(Long id) {
        return folderRepository.findById(id);
    }

    public List<Folder> getAllFolders() {
        return folderRepository.findAll();
    }

    public long getImageCount(Long folderId) {
        return imageMetadataRepository.findByFolderId(folderId).size();
    }

    @Transactional
    public Folder createFolder(String name, Long parentId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Folder name must not be empty");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("Folder name must not exceed 255 characters");
        }

        if (parentId != null) {
            folderRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent folder not found"));
            int depth = getDepth(parentId);
            if (depth >= MAX_DEPTH) {
                throw new IllegalArgumentException("Maximum folder nesting depth (" + MAX_DEPTH + ") exceeded");
            }
        }

        boolean exists = parentId == null
            ? folderRepository.findByNameAndParentIdIsNull(name).isPresent()
            : folderRepository.findByNameAndParentId(name, parentId).isPresent();
        if (exists) {
            throw new IllegalArgumentException("A folder with this name already exists at this level");
        }

        return folderRepository.save(new Folder(name, parentId));
    }

    @Transactional
    public Folder renameFolder(Long id, String newName) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Folder name must not be empty");
        }

        boolean exists = folder.getParentId() == null
            ? folderRepository.findByNameAndParentIdIsNull(newName).isPresent()
            : folderRepository.findByNameAndParentId(newName, folder.getParentId()).isPresent();
        if (exists && !newName.equals(folder.getName())) {
            throw new IllegalArgumentException("A folder with this name already exists at this level");
        }

        folder.setName(newName);
        return folderRepository.save(folder);
    }

    @Transactional
    public void deleteFolder(Long id) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));

        // Move images in this folder to root
        List<ImageMetadata> images = imageMetadataRepository.findByFolderId(id);
        for (ImageMetadata image : images) {
            image.setFolderId(null);
            imageMetadataRepository.save(image);
        }

        // Recursively handle subfolders
        List<Folder> subfolders = folderRepository.findByParentId(id);
        for (Folder subfolder : subfolders) {
            deleteFolder(subfolder.getId());
        }

        folderRepository.delete(folder);
    }

    @Transactional
    public void moveImageToFolder(String imageId, Long folderId) {
        ImageMetadata metadata = imageMetadataRepository.findById(imageId)
                .orElseThrow(() -> new IllegalArgumentException("Image not found"));

        if (folderId != null) {
            folderRepository.findById(folderId)
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        }

        metadata.setFolderId(folderId);
        imageMetadataRepository.save(metadata);
    }

    @Transactional
    public void moveImageByStorageKey(String storageKey, Long folderId) {
        ImageMetadata metadata = imageMetadataRepository.findByStorageKey(storageKey)
                .orElseThrow(() -> new IllegalArgumentException("Image not found"));

        if (folderId != null) {
            folderRepository.findById(folderId)
                    .orElseThrow(() -> new IllegalArgumentException("Folder not found"));
        }

        metadata.setFolderId(folderId);
        imageMetadataRepository.save(metadata);
    }

    public List<Folder> getBreadcrumbs(Long folderId) {
        List<Folder> breadcrumbs = new ArrayList<>();
        Long currentId = folderId;
        while (currentId != null) {
            Folder folder = folderRepository.findById(currentId).orElse(null);
            if (folder == null) break;
            breadcrumbs.add(folder);
            currentId = folder.getParentId();
        }
        Collections.reverse(breadcrumbs);
        return breadcrumbs;
    }

    private int getDepth(Long folderId) {
        int depth = 0;
        Long currentId = folderId;
        while (currentId != null) {
            depth++;
            Folder folder = folderRepository.findById(currentId).orElse(null);
            if (folder == null) break;
            currentId = folder.getParentId();
        }
        return depth;
    }
}
