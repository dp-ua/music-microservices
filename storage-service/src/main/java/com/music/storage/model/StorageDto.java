package com.music.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
