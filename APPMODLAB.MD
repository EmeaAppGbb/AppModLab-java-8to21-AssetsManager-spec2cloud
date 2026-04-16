---
title: "Assets Manager Java 8→21 Modernization with Spec2Cloud"
description: "Modernize a Java 8 Spring Boot app from AWS to Azure (Java 21, Spring Boot 3.x, Blob Storage, Service Bus) using Spec2Cloud & Copilot."
authors: ["marconsilva"]
category: "Code Modernization"
industry: "Cross-Industry"
services: ["Azure Blob Storage", "Azure Service Bus", "Azure Database for PostgreSQL"]
languages: ["Java"]
frameworks: []
modernizationTools: ["Spec2Cloud"]
tags: ["java-modernization", "spring-boot", "aws-to-azure", "spec-driven-development", "cloud-migration"]
extensions: []
thumbnail: "https://raw.githubusercontent.com/EmeaAppGbb/AppModLab-java-8to21-AssetsManager-spec2cloud/refs/heads/main/img/repo-banner.png"
video: ""
version: ""
---

## Overview

This lab walks you through modernizing a **Java 8 Spring Boot 2.7** application—the **Assets Manager**—from AWS to Azure. Using the **Spec2Cloud** methodology and GitHub Copilot, you will upgrade the application to Java 21 and Spring Boot 3.x, replacing AWS services (S3, SQS) with their Azure counterparts (Blob Storage, Service Bus) while migrating the database to Azure Database for PostgreSQL. The goal is a fully cloud-native Azure deployment driven by specification documents rather than manual refactoring.

## The Legacy Application

The Assets Manager is a multi-module Spring Boot web application that allows users to upload, browse, and manage image assets. It stores files in S3-compatible object storage, tracks metadata in PostgreSQL, and uses a message queue for asynchronous processing.

The home page presents a storage listing view where users can see all uploaded assets at a glance:

![Assets Manager Home Page — storage listing showing uploaded image assets](assets/screenshots/01-home-storage-listing.png)

The core workflow revolves around file management. Users select an image file through the upload form, which supports JPG, PNG, and GIF formats up to 10 MB:

![Upload form with a file selected and previewed before submission](assets/screenshots/03-upload-with-file.png)

After a successful upload, the asset appears in the gallery with its metadata (file size, timestamp) and actions for viewing or deleting:

![Gallery view after a successful upload showing the new asset with metadata and actions](assets/screenshots/04-after-upload.png)

This legacy version runs on Java 8 with AWS SDK dependencies and Docker-based local services (MinIO for S3, RabbitMQ, PostgreSQL). The modernization process will replace all of these with Azure-native equivalents.

## Initial Application Screenshots

The following screenshots show the **legacy Java 8 / Spring Boot 2.7 / AWS** version of the Assets Manager application before modernization. The app was run locally using Docker containers for PostgreSQL, RabbitMQ, and MinIO (S3-compatible storage).

> **Note:** The original `AwsS3Config.java` in the web module was missing a `@Bean` annotation on the `s3Client()` method, which prevented the application from starting. This was temporarily patched during testing — it is a known issue in the legacy codebase that modernization should address.

### Storage Listing (Home Page)
Empty state — no images uploaded yet.

![Storage Listing](assets/screenshots/01-home-storage-listing.png)

### Upload Page
Form for uploading images to S3 storage (supports JPG, PNG, GIF up to 10 MB).

![Upload Page](assets/screenshots/02-upload-page.png)

### Upload With File Selected
A sample image selected and previewed before upload.

![Upload With File](assets/screenshots/03-upload-with-file.png)

### After Successful Upload
Image uploaded successfully and displayed in the gallery with metadata (size, timestamp) and View/Delete actions.

![After Upload](assets/screenshots/04-after-upload.png)