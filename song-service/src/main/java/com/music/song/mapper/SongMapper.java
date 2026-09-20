package com.music.song.mapper;

import static org.mapstruct.MappingConstants.ComponentModel.SPRING;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.music.song.controller.dto.MetadataUploadDto;
import com.music.song.controller.dto.MetadataUploadedDto;
import com.music.song.model.MetadataDto;
import com.music.song.model.Song;

@Mapper(componentModel = SPRING)
public interface SongMapper {

    @Mapping(target = "isDeleted", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Song toEntity(MetadataUploadDto metadataUploadDto);

    MetadataDto toMetadataDto(Song song);

    MetadataUploadedDto toMetadataUploadedDto(Song song);

}
