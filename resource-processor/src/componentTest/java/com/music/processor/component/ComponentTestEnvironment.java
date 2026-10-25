package com.music.processor.component;

import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

/**
 * Everything around the service under test, started once for the whole run (not per scenario):
 * a real RabbitMQ in a Docker container and two WireMock servers that play resource-service and song-service.
 * <p>
 * Started in a static block, because the Spring context (which needs the ports) is created once and cached.
 */
final class ComponentTestEnvironment {

    static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3-management-alpine"));

    static final WireMockServer RESOURCE_SERVICE = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    static final WireMockServer SONG_SERVICE = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        RABBIT.start();
        RESOURCE_SERVICE.start();
        SONG_SERVICE.start();
        // the container is removed by Testcontainers (Ryuk) when the JVM ends, the HTTP servers are stopped here
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            RESOURCE_SERVICE.stop();
            SONG_SERVICE.stop();
        }));
    }

    private ComponentTestEnvironment() {
    }

}
