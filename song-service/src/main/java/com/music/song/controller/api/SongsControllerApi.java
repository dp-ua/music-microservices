package com.music.song.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.music.song.controller.dto.MetadataUploadDto;
import com.music.song.controller.dto.MetadataUploadedDto;
import com.music.song.controller.dto.SongsDeletedDto;
import com.music.song.model.MetadataDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Songs Controller Api")
public interface SongsControllerApi {

    @Operation(summary = "Upload song metadata")
    @ApiResponse(responseCode = "201", description = "Metadata uploaded successfully")
    @ApiResponse(responseCode = "400", description = "Bad request")
    ResponseEntity<MetadataUploadedDto> uploadMetadata(@Parameter @Valid MetadataUploadDto metadataDto);

    @Operation(summary = "Get song metadata")
    @ApiResponse(responseCode = "200", description = "Metadata retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Bad request")
    @ApiResponse(responseCode = "404", description = "Metadata not found")
    ResponseEntity<MetadataDto> getMetadata(@Parameter long id);

    @Operation(summary = "Delete songs metadata")
    @ApiResponse(responseCode = "200", description = "Request successful, songs deleted as specified")
    @ApiResponse(responseCode = "400", description = "Bad request")
    ResponseEntity<SongsDeletedDto> deleteSongs(@Parameter List<Long> ids);

}
