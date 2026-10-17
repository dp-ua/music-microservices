package com.music.resource.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "messaging.resource-uploaded")
public class MessagingProperties {

    /**
     * Name of the durable queue the events are published to.
     */
    private String queue;

    /**
     * How long to wait for the broker to confirm that the message is stored.
     */
    private Duration confirmTimeout;

}
