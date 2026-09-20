package com.music.song.controller.v1;

import static org.springframework.http.HttpStatus.CREATED;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.music.song.controller.api.SongsControllerApi;
import com.music.song.controller.dto.MetadataUploadDto;
import com.music.song.controller.dto.MetadataUploadedDto;
import com.music.song.controller.dto.SongsDeletedDto;
import com.music.song.model.MetadataDto;
import com.music.song.service.SongsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/songs")
@Validated
@Slf4j
public class SongsController implements SongsControllerApi {

    private final SongsService songsService;

    @Override
    @PostMapping
    public ResponseEntity<MetadataUploadedDto> uploadMetadata(@RequestBody @Valid MetadataUploadDto metadata) {
        log.info("Uploading metadata for resourceId: {}", metadata.getResourceId());
        return ResponseEntity
                .status(CREATED)
                .body(songsService.uploadMetadata(metadata));
    }

    @Override
    @GetMapping(value = "/{resourceId}")
    public ResponseEntity<MetadataDto> getMetadata(@PathVariable long resourceId) {
        log.info("Retrieving metadata for resourceId: {}", resourceId);
        return ResponseEntity.ok(songsService.getMetadata(resourceId));
    }

    @Override
    @DeleteMapping
    public ResponseEntity<SongsDeletedDto> deleteSongs(@RequestParam List<Long> ids) {
        log.info("Deleting songs with IDs: {}", ids);
        return ResponseEntity.ok(songsService.deleteSongs(ids));
    }

}
