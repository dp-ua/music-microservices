package com.music.processor.client.resource;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Found by name in Eureka, every call is balanced between all running instances.
 */
@FeignClient(name = "resource-service")
public interface ResourceServiceClient {

    @GetMapping(path = "/resources/{id}", produces = "audio/mpeg")
    byte[] download(@PathVariable("id") Long id);

}
