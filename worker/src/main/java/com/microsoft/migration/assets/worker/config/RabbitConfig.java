package com.microsoft.migration.assets.worker.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.microsoft.migration.assets.worker.service.AbstractFileProcessingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String IMAGE_PROCESSING_QUEUE = "image-processing";

    @Value("${azure.servicebus.connection-string}")
    private String connectionString;

    @Bean
    public ServiceBusProcessorClient serviceBusProcessorClient(AbstractFileProcessingService processingService) {
        var processorClient = new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .processor()
                .queueName(IMAGE_PROCESSING_QUEUE)
                .processMessage(processingService::processMessage)
                .processError(processingService::processError)
                .buildProcessorClient();
        
        processorClient.start();
        return processorClient;
    }
}
