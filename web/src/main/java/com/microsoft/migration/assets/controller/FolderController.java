package com.microsoft.migration.assets.controller;

import com.microsoft.migration.assets.common.model.Folder;
import com.microsoft.migration.assets.constants.StorageConstants;
import com.microsoft.migration.assets.service.FolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/" + StorageConstants.STORAGE_PATH + "/folders")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping
    public String createFolder(@RequestParam String name,
                              @RequestParam(required = false) Long parentId,
                              RedirectAttributes redirectAttributes) {
        try {
            folderService.createFolder(name, parentId);
            redirectAttributes.addFlashAttribute("success", "Folder created successfully");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        if (parentId != null) {
            return "redirect:/" + StorageConstants.STORAGE_PATH + "?folderId=" + parentId;
        }
        return "redirect:/" + StorageConstants.STORAGE_PATH;
    }

    @PostMapping("/{id}/rename")
    public String renameFolder(@PathVariable Long id,
                              @RequestParam String name,
                              RedirectAttributes redirectAttributes) {
        try {
            Folder folder = folderService.renameFolder(id, name);
            redirectAttributes.addFlashAttribute("success", "Folder renamed successfully");
            if (folder.getParentId() != null) {
                return "redirect:/" + StorageConstants.STORAGE_PATH + "?folderId=" + folder.getParentId();
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/" + StorageConstants.STORAGE_PATH;
    }

    @PostMapping("/{id}/delete")
    public String deleteFolder(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Folder folder = folderService.getFolder(id).orElse(null);
            Long parentId = folder != null ? folder.getParentId() : null;

            folderService.deleteFolder(id);
            redirectAttributes.addFlashAttribute("success", "Folder deleted successfully");

            if (parentId != null) {
                return "redirect:/" + StorageConstants.STORAGE_PATH + "?folderId=" + parentId;
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/" + StorageConstants.STORAGE_PATH;
    }
}
