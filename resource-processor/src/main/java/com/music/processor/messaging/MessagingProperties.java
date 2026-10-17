package com.music.processor.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "messaging.resource-uploaded")
public class MessagingProperties {

    /**
     * Name of the durable queue the events are read from.
     */
    private String queue;

}
