package com.music.resource.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import org.mapstruct.Mapper;

import com.music.resource.client.song.dto.ClientMetadataUploadDto;
import com.music.resource.model.MetadataDto;

@Mapper(componentModel = SPRING)
public interface MetadataMapper {

    ClientMetadataUploadDto toClientDtoWithResourceId(MetadataDto metadata, Long resourceId);

}