# FRD: File Operation Logging

**Feature ID**: F-009
**Status**: Draft
**Priority**: P2 (Medium)
**Last Updated**: 2026-03-26

## Description

File Operation Logging provides request-level observability for all storage operations through a Spring MVC interceptor. The `FileOperationLoggingInterceptor` (inner class of `WebMvcConfig`) captures the HTTP method, URI, operation type, start time, duration, response status, and error messages for all requests matching `/storage/**`, excluding file download endpoints (`/storage/view/**`) to reduce noise. Operation types are classified as: FILE_UPLOAD, FILE_DELETE, FILE_DOWNLOAD, FILE_VIEW_PAGE, FILE_LIST, or FILE_OPERATION (fallback).

## User Stories

### US-F009-001: Monitor File Operations

**As a** System Operator
**I want to** see logs of all file operations with timing information
**So that** I can monitor application usage and diagnose performance issues

**Acceptance Criteria:**
- GIVEN a user uploads a file WHEN the request completes THEN a log entry shows the operation type, URI, duration in ms, and HTTP status
- GIVEN a file operation fails WHEN an exception occurs THEN the log entry includes the error message

## Functional Requirements

### FR-F009-001: Intercept File Operations

- Input: Any HTTP request matching `/storage/**` (excluding `/storage/view/**`)
- Processing: `preHandle()` captures start time. `afterCompletion()` calculates duration and logs operation details.
- Output: Console log line in format `[FILE-OP] {method} {uri} - {operation} completed/FAILED in {ms} ms (Status: {code})`
- Error handling: If an exception is available in `afterCompletion`, it is included in the log.

### FR-F009-002: Operation Type Detection

- Input: Request URI and HTTP method
- Processing: Pattern matching on URI: `/upload` → FILE_UPLOAD, `/delete/` → FILE_DELETE, `/view/` → FILE_DOWNLOAD, `/view-page/` → FILE_VIEW_PAGE, GET on base path → FILE_LIST, else → FILE_OPERATION
- Output: String operation type identifier

## Dependencies

| Dependency | Type | Direction | Description |
|------------|------|-----------|-------------|
| Spring MVC | Infrastructure | — | Interceptor registration |
| All F-001 through F-005 | Feature | Upstream | Operations being logged |

---

## Current Implementation (Brownfield Extension)

### Files Involved

| File Path | Role | Lines |
|-----------|------|-------|
| `web/src/main/java/.../config/WebMvcConfig.java` | Interceptor registration + implementation | 226-309 |

### Test Coverage

| Test Type | Files | Assertions | Coverage |
|-----------|-------|------------|----------|
| Unit | — | 0 | 0% |

### Known Limitations

- Uses `System.out.printf()` instead of SLF4J (assessment finding P3)
- Extends deprecated `HandlerInterceptorAdapter` (assessment finding P2)
- Operation detection uses if-else chains with `String.contains()` — fragile and imprecise
- `WebMvcConfig` extends deprecated `WebMvcConfigurerAdapter` (assessment finding P1)
