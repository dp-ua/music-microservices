package com.music.song.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.music.song.controller.dto.MetadataUploadDto;
import com.music.song.controller.dto.MetadataUploadedDto;
import com.music.song.controller.dto.SongsDeletedDto;
import com.music.song.exception.MetadataWithIdExistException;
import com.music.song.exception.ResourceNotFoundException;
import com.music.song.mapper.SongMapper;
import com.music.song.model.MetadataDto;
import com.music.song.model.Song;
import com.music.song.repository.SongsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongsServiceImpl implements SongsService {

    private final SongMapper songMapper;
    private final SongsRepository songsRepository;

    @Override
    public MetadataUploadedDto uploadMetadata(MetadataUploadDto metadataDto) {
        var resourceId = metadataDto.getResourceId();

        if (songsRepository.existsByResourceId(resourceId)) {
            throw MetadataWithIdExistException.byId(resourceId);
        }

        var song = songMapper.toEntity(metadataDto);
        songsRepository.save(song);
        log.info("Song saved: {}", song);

        return songMapper.toMetadataUploadedDto(song);
    }

    @Override
    public MetadataDto getMetadata(long resourceId) {
        log.info("Getting song with id {}", resourceId);
        return songsRepository.findByResourceId(resourceId)
                .filter(song -> !song.getIsDeleted())
                .map(songMapper::toMetadataDto)
                .orElseThrow(() -> ResourceNotFoundException.byId(resourceId));
    }

    @Override
    public SongsDeletedDto deleteSongs(List<Long> resourceIds) {
        var existingIds = songsRepository.findAllByResourceIdInAndNotDeleted(resourceIds).stream()
                .map(Song::getId)
                .toList();

        if (!existingIds.isEmpty()) {
            songsRepository.softDeleteByIdIn(existingIds);
        }

        log.info("delete resources: {}", existingIds);
        return SongsDeletedDto.builder()
                .ids(existingIds)
                .build();
    }

}
