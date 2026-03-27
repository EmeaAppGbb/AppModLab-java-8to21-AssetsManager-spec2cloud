package com.microsoft.migration.assets.worker;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableRabbit
@EntityScan(basePackages = "com.microsoft.migration.assets.common.model")
@EnableJpaRepositories(basePackages = "com.microsoft.migration.assets.common.repository")
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(WorkerApplication.class);
        application.addListeners(new ApplicationPidFileWriter());
        application.run(args);
    }
}