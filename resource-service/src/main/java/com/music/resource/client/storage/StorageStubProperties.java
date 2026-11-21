package com.music.resource.client.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Stub data that emulates the response of storage-service when it is unavailable (circuit breaker fallback).
 * The defaults match the storages pre-created by storage-service.
 */
@Data
@ConfigurationProperties(prefix = "storage.stub")
public class StorageStubProperties {

    private StorageDto staging = new StorageDto(1L, StorageType.STAGING, "staging-bucket", "/files");
    private StorageDto permanent = new StorageDto(2L, StorageType.PERMANENT, "permanent-bucket", "/files");

}
