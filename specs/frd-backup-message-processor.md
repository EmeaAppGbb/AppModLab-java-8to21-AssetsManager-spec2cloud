# FRD: Backup Message Processor

**Feature ID**: F-010
**Status**: Draft
**Priority**: P3 (Low)
**Last Updated**: 2026-03-26

## Description

The Backup Message Processor is an optional RabbitMQ consumer in the web module that monitors the `image-processing` queue for logging and resilience purposes. It is activated only when the `backup` Spring profile is explicitly enabled. When active, it consumes messages from the same queue as the worker, logs the message metadata (key, content type, storage type, size), and acknowledges the message. It uses manual acknowledgment with NACK+requeue on failure, providing a safety net for message monitoring.

## User Stories

### US-F010-001: Monitor Message Queue

**As a** System Operator
**I want to** optionally enable backup message monitoring
**So that** I can observe queue activity without affecting the worker

**Acceptance Criteria:**
- GIVEN the `backup` profile is active WHEN a message arrives on the queue THEN the message metadata is logged at INFO level and the message is acknowledged
- GIVEN the `backup` profile is NOT active WHEN the application starts THEN the backup processor bean is not created

## Functional Requirements

### FR-F010-001: Consume and Log Messages

- Input: `ImageProcessingMessage` from `image-processing` queue
- Processing: Log message key, content type, storage type, and size via SLF4J. Acknowledge with `channel.basicAck()`.
- Output: Log entries prefixed with `[BACKUP]`
- Error handling: On exception, NACK with requeue (`basicNack(deliveryTag, false, true)`). Log error. On NACK failure, log the acknowledgment error.

## Dependencies

| Dependency | Type | Direction | Description |
|------------|------|-----------|-------------|
| RabbitMQ | External | — | Message consumption |
| F-006 Async Thumbnail | Feature | Upstream | Shares the same queue |

---

## Current Implementation (Brownfield Extension)

### Files Involved

| File Path | Role | Lines |
|-----------|------|-------|
| `web/src/main/java/.../service/BackupMessageProcessor.java` | Backup consumer | 916-949 |

### Test Coverage

| Test Type | Files | Assertions | Coverage |
|-----------|-------|------------|----------|
| Unit | — | 0 | 0% |

### Known Limitations

- Competes with the worker for messages if both are consuming the same queue simultaneously — could steal messages from the worker
- String concatenation in `log.error()` instead of parameterized logging (assessment finding P6)
- Only useful for monitoring — does not provide actual backup or retry functionality
