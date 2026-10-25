package com.music.resource.messaging;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.music.resource.configuration.RabbitConfig;

/**
 * Smallest Spring context that has the real publisher: the production RabbitMQ configuration (queue, JSON converter,
 * retry) and the Spring Boot RabbitMQ auto-configuration (publisher confirms and returns from application.yml).
 * No database, no S3, no Feign, no Eureka.
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration({RabbitAutoConfiguration.class, AopAutoConfiguration.class})
@Import({RabbitConfig.class, RabbitResourceEventPublisher.class})
class RabbitPublisherTestConfig {

}
