package com.microsoft.migration.assets.common.repository;

import com.microsoft.migration.assets.common.model.ImageMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImageMetadataRepository extends JpaRepository<ImageMetadata, String> {
    Optional<ImageMetadata> findByStorageKey(String storageKey);
    List<ImageMetadata> findByFolderIdIsNull();
    List<ImageMetadata> findByFolderId(Long folderId);
}
