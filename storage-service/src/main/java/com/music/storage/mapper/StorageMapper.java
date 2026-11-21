package com.music.storage.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.music.storage.controller.dto.StorageCreateDto;
import com.music.storage.model.Storage;
import com.music.storage.model.StorageDto;

@Mapper(componentModel = SPRING)
public interface StorageMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Storage toEntity(StorageCreateDto dto);

    StorageDto toDto(Storage storage);

}
