package com.music.song.service;

import org.springframework.stereotype.Service;

import com.music.common.validator.CSVValidator;
import com.music.song.controller.dto.MetadataUploadDto;
import com.music.song.controller.dto.MetadataUploadedDto;
import com.music.song.controller.dto.SongsDeletedDto;
import com.music.song.exception.MetadataWithIdExistException;
import com.music.song.exception.SongNotFoundException;
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
    private final CSVValidator validator;

    @Override
    public MetadataUploadedDto uploadMetadata(MetadataUploadDto metadataDto) {
        var id = metadataDto.getId();

        if (songsRepository.existsById(id)) {
            throw MetadataWithIdExistException.byId(id);
        }

        var song = songMapper.toEntity(metadataDto);
        songsRepository.save(song);
        log.info("Song saved: {}", song);

        return songMapper.toMetadataUploadedDto(song);
    }

    @Override
    public MetadataDto getMetadata(long id) {
        log.info("Getting song with id {}", id);
        return songsRepository.findById(id)
                .filter(song -> !song.getIsDeleted())
                .map(songMapper::toMetadataDto)
                .orElseThrow(() -> SongNotFoundException.byId(id));
    }

    @Override
    public SongsDeletedDto deleteSongs(String rawIds) {
        var ids = validator.parseAndValidateIds(rawIds);
        log.info("Delete songs by id's: [{}]", ids);

        var existingIds = songsRepository.findAllByIdInAndNotDeleted(ids).stream()
                .map(Song::getId)
                .toList();

        if (!existingIds.isEmpty()) {
            songsRepository.softDeleteByIdIn(existingIds);
        }

        log.info("Resources deleted id's: [{}]", existingIds);
        return SongsDeletedDto.builder()
                .ids(existingIds)
                .build();
    }

}
