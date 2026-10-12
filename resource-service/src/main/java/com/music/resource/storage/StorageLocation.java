package com.music.resource.storage;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StorageLocation {

    private String bucket;
    private String key;

}
