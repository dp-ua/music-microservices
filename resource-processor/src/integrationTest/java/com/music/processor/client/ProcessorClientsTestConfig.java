package com.music.processor.client;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;

import com.music.processor.client.resource.ResourceServiceClient;
import com.music.processor.client.resource.ResourceServiceClientProxyImpl;
import com.music.processor.client.song.SongServiceClient;
import com.music.processor.client.song.SongServiceClientProxyImpl;

/**
 * Smallest Spring context with the real Feign clients and the real retrying proxies, without RabbitMQ,
 * Eureka and the processing service. The clients are pointed to WireMock by properties of the test.
 */
@Configuration(proxyBeanMethods = false)
@EnableRetry
@EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
@EnableFeignClients(clients = {ResourceServiceClient.class, SongServiceClient.class})
@Import({ResourceServiceClientProxyImpl.class, SongServiceClientProxyImpl.class})
class ProcessorClientsTestConfig {

}
