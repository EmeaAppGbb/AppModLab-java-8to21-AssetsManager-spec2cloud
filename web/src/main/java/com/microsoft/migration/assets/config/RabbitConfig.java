package com.microsoft.migration.assets.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String IMAGE_PROCESSING_QUEUE = "image-processing";

    @Value("${azure.servicebus.connection-string}")
    private String connectionString;

    @Bean
    public ServiceBusSenderClient serviceBusSenderClient() {
        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .queueName(IMAGE_PROCESSING_QUEUE)
                .buildClient();
    }
}