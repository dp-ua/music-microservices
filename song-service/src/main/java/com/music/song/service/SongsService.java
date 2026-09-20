package com.music.song.service;

import com.music.song.controller.dto.MetadataUploadDto;
import com.music.song.controller.dto.MetadataUploadedDto;
import com.music.song.controller.dto.SongsDeletedDto;
import com.music.song.model.MetadataDto;

import jakarta.validation.Valid;

public interface SongsService {

    MetadataUploadedDto uploadMetadata(@Valid MetadataUploadDto metadataDto);

    MetadataDto getMetadata(long id);

    SongsDeletedDto deleteSongs(String ids);

}
