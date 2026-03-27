package com.microsoft.migration.assets.common.repository;

import com.microsoft.migration.assets.common.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByParentIdIsNull();
    List<Folder> findByParentId(Long parentId);
    Optional<Folder> findByNameAndParentId(String name, Long parentId);
    Optional<Folder> findByNameAndParentIdIsNull(String name);
}
