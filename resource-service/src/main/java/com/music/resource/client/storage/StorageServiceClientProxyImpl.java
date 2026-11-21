package com.music.resource.client.storage;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceClientProxyImpl implements StorageServiceClientProxy {

    /** Name of the circuit breaker instance, see resilience4j.circuitbreaker in application.yml. */
    public static final String CIRCUIT_BREAKER = "storage-service";

    private final StorageServiceClient storageServiceClient;
    private final StorageStubProperties stub;

    @Override
    @CircuitBreaker(name = CIRCUIT_BREAKER, fallbackMethod = "getStoragesFallback")
    public List<StorageDto> getStorages() {
        var storages = storageServiceClient.getStorages();
        log.debug("Storages received from storage-service: {}", storages);
        return storages;
    }

    /** Called when the call fails or the circuit is open. */
    List<StorageDto> getStoragesFallback(Throwable cause) {
        log.warn("storage-service is unavailable ({}), using stub storages", cause.toString());
        return List.of(stub.getStaging(), stub.getPermanent());
    }

}
