package com.music.processor.contract;

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
 * The real Feign clients and the real retrying proxies of resource-processor, nothing else (no RabbitMQ, no Eureka).
 * They talk to the Pact mock servers.
 */
@Configuration(proxyBeanMethods = false)
@EnableRetry
@EnableAutoConfiguration(exclude = RabbitAutoConfiguration.class)
@EnableFeignClients(clients = {ResourceServiceClient.class, SongServiceClient.class})
@Import({ResourceServiceClientProxyImpl.class, SongServiceClientProxyImpl.class})
class ContractClientsConfig {

}
