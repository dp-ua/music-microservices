package com.music.resource.controller.api;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.music.resource.controller.dto.ResourceUploadedDto;
import com.music.resource.controller.dto.ResourcesDeletedDto;
import com.music.resource.validation.ValidMp3File;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;

@Tag(name = "Resource Controller Api")
public interface ResourcesControllerApi {

    @Operation(summary = "Upload MP3 resource")
    @ApiResponse(responseCode = "201", description = "Resource uploaded successfully")
    @ApiResponse(responseCode = "400", description = "Bad request")
    ResponseEntity<ResourceUploadedDto> uploadResource(@RequestParam("file") @ValidMp3File MultipartFile file);

    @Operation(summary = "Get resource")
    @ApiResponse(responseCode = "200", description = "Resource retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Bad request")
    @ApiResponse(responseCode = "404", description = "Resource not found")
    ResponseEntity<Resource> getResource(@PathVariable @Positive Long id);

    @Operation(summary = "Delete resources")
    @ApiResponse(responseCode = "200", description = "Request successful, resources deleted as specified")
    @ApiResponse(responseCode = "400", description = "Bad request")
    ResponseEntity<ResourcesDeletedDto> deleteResources(@RequestParam String id);

}
