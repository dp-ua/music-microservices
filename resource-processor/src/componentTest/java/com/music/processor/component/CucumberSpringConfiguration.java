package com.music.processor.component;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.music.processor.ResourceProcessorApplication;

import io.cucumber.spring.CucumberContextConfiguration;

/**
 * Starts the whole resource-processor (the real application class, the real application.yml) once for all scenarios.
 * Only the surroundings are replaced by properties: where RabbitMQ is, where the neighbour services are, no Eureka.
 * Retry settings are the production ones (3 attempts, 500 ms and 1 s pauses).
 */
@CucumberContextConfiguration
@SpringBootTest(
        classes = ResourceProcessorApplication.class,
        properties = "eureka.client.enabled=false")
public class CucumberSpringConfiguration {

    @DynamicPropertySource
    static void surroundings(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", ComponentTestEnvironment.RABBIT::getHost);
        registry.add("spring.rabbitmq.port", ComponentTestEnvironment.RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", ComponentTestEnvironment.RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", ComponentTestEnvironment.RABBIT::getAdminPassword);
        // Feign clients are found by name; here the names point straight to WireMock
        registry.add("spring.cloud.openfeign.client.config.resource-service.url",
                ComponentTestEnvironment.RESOURCE_SERVICE::baseUrl);
        registry.add("spring.cloud.openfeign.client.config.song-service.url",
                ComponentTestEnvironment.SONG_SERVICE::baseUrl);
    }

}
