package com.music.storage.controller.api;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.music.storage.controller.dto.StorageCreateDto;
import com.music.storage.controller.dto.StorageCreatedDto;
import com.music.storage.controller.dto.StoragesDeletedDto;
import com.music.storage.model.StorageDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Storage Controller Api")
public interface StorageControllerApi {

    @Operation(summary = "Create storage")
    @ApiResponse(responseCode = "200", description = "Storage created successfully")
    @ApiResponse(responseCode = "400", description = "Validation errors in the request body")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    ResponseEntity<StorageCreatedDto> createStorage(@Parameter @Valid StorageCreateDto storage);

    @Operation(summary = "Get all storages")
    @ApiResponse(responseCode = "200", description = "Storages retrieved successfully")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    ResponseEntity<List<StorageDto>> getStorages();

    @Operation(summary = "Delete storages by comma-separated ids")
    @ApiResponse(responseCode = "200", description = "Request successful, storages deleted as specified")
    @ApiResponse(responseCode = "400", description = "Invalid CSV format or CSV too long")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    ResponseEntity<StoragesDeletedDto> deleteStorages(@Parameter String id);

}
