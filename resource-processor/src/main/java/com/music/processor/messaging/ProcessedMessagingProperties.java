package com.music.processor.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "messaging.resource-processed")
public class ProcessedMessagingProperties {

    /**
     * Name of the durable queue the "resource processed" events are published to.
     */
    private String queue;

}
