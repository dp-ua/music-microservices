package com.music.resource.client.storage;

import org.springframework.stereotype.Service;

import com.music.resource.exception.StorageException;

import lombok.RequiredArgsConstructor;

/**
 * Finds the place for the files of a given type: asks storage-service (or the stub when it is down).
 */
@Service
@RequiredArgsConstructor
public class StorageRegistry {

    private final StorageServiceClientProxy storageServiceClient;

    public StorageDto get(StorageType type) {
        return storageServiceClient.getStorages().stream()
                .filter(storage -> storage.getStorageType() == type)
                .findFirst()
                .orElseThrow(() -> StorageException.notConfigured(type.name()));
    }

}
