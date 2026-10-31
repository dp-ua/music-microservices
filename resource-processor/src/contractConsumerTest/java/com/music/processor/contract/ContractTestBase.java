package com.music.processor.contract;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * One Spring context for all HTTP contract tests of the processor. The Pact mock servers listen on fixed ports
 * (the context is created once and keeps the addresses of the Feign clients), the ports are the constants below.
 */
@SpringBootTest(
        classes = ContractClientsConfig.class,
        properties = {
                "spring.application.name=resource-processor-contract",
                "eureka.client.enabled=false",
                "processor.retry.max-attempts=3",
                "processor.retry.delay-ms=10",
                "processor.retry.multiplier=1",
                "spring.cloud.openfeign.client.config.resource-service.url=http://localhost:18081",
                "spring.cloud.openfeign.client.config.song-service.url=http://localhost:18082"
        })
abstract class ContractTestBase {

    static final String RESOURCE_SERVICE_PORT = "18081";
    static final String SONG_SERVICE_PORT = "18082";

}
