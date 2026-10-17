package com.music.processor.configuration;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import com.music.processor.messaging.MessagingProperties;

@Configuration
@EnableRetry
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitConfig {

    /**
     * Must be declared exactly as in resource-service, the broker rejects a redeclaration with other settings.
     */
    @Bean
    public Queue resourceUploadedQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.getQueue()).build();
    }

    /**
     * The message class is taken from the listener parameter, the type header of the sender is ignored.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
