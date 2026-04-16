package com.microsoft.migration.assets.service;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.migration.assets.model.ImageProcessingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A backup message processor that serves as a monitoring and logging service.
 * 
 * Only enabled when the "backup" profile is active.
 */
@Slf4j
@Component
@Profile("backup") 
public class BackupMessageProcessor {

    private final ObjectMapper objectMapper;

    public BackupMessageProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Processes image messages from Service Bus for monitoring and resilience purposes.
     */
    public void processBackupMessage(ServiceBusReceivedMessageContext context) {
        var receivedMessage = context.getMessage();
        try {
            var message = objectMapper.readValue(receivedMessage.getBody().toString(), ImageProcessingMessage.class);
            log.info("[BACKUP] Monitoring message: {}", message.key());
            log.info("[BACKUP] Content type: {}, Storage: {}, Size: {}", 
                    message.contentType(), message.storageType(), message.size());
            
            // Complete the message
            context.complete();
            log.info("[BACKUP] Successfully processed message: {}", message.key());
        } catch (Exception e) {
            log.error("[BACKUP] Failed to process message", e);
            context.abandon();
        }
    }
}