package com.music.resource.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.music.resource.model.Resource;

@Mapper(componentModel = SPRING)
public interface ResourceMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Resource toEntity(String fileName, byte[] fileData, Long fileSize, String contentType);

}
