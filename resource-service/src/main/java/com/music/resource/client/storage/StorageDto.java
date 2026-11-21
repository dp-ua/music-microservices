package com.music.resource.client.storage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Storage of storage-service: {"id": 1, "storageType": "STAGING", "bucket": "staging-bucket", "path": "/files"}.
 * The stub data of the circuit breaker fallback has the same structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageDto {

    private Long id;
    private StorageType storageType;
    private String bucket;
    private String path;

}
