package com.microsoft.migration.assets.service;

import com.microsoft.migration.assets.common.model.ImageProcessingMessage;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BackupMessageProcessorTest {

    @Mock
    private Channel channel;

    private final BackupMessageProcessor processor = new BackupMessageProcessor();

    @Test
    void processBackupMessage_success_acknowledgesMessage() throws Exception {
        var message = new ImageProcessingMessage("test.jpg", "image/jpeg", "local", 1024L);
        long deliveryTag = 1L;

        processor.processBackupMessage(message, channel, deliveryTag);

        verify(channel).basicAck(deliveryTag, false);
    }

    @Test
    void processBackupMessage_failure_rejectsMessage() throws Exception {
        var message = new ImageProcessingMessage("test.jpg", "image/jpeg", "local", 1024L);
        long deliveryTag = 2L;

        doThrow(new IOException("Simulated ACK failure"))
                .doNothing()
                .when(channel).basicAck(deliveryTag, false);

        processor.processBackupMessage(message, channel, deliveryTag);

        verify(channel).basicNack(deliveryTag, false, true);
    }
}
