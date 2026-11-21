package com.music.resource.client.storage;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "storage-service")
public interface StorageServiceClient {

    @GetMapping("${services.config.storage-service-client.urls.get-storages}")
    List<StorageDto> getStorages();

}
