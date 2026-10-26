package com.music.resource.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.music.resource.model.AudioResource;
import com.music.resource.model.ResourceFile;
import com.music.resource.storage.StorageLocation;

@Mapper(componentModel = SPRING)
public interface ResourceMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "storageBucket", source = "location.bucket")
    @Mapping(target = "storageKey", source = "location.key")
    @Mapping(target = "fileSize", source = "fileSize")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AudioResource toEntity(StorageLocation location, Long fileSize);

    @Mapping(target = "data", source = "data")
    @Mapping(target = "size", source = "resource.fileSize")
    ResourceFile toResourceFile(AudioResource resource, byte[] data);

    @Mapping(target = "bucket", source = "storageBucket")
    @Mapping(target = "key", source = "storageKey")
    StorageLocation toLocation(AudioResource resource);

}
