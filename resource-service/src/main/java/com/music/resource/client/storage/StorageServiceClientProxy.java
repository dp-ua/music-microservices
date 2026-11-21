package com.music.resource.client.storage;

import java.util.List;

public interface StorageServiceClientProxy {

    /**
     * All storages of storage-service. Protected by a circuit breaker: if storage-service is unavailable
     * (or the circuit is open), the stub storages are returned instead.
     */
    List<StorageDto> getStorages();

}
