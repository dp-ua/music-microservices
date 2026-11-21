package com.music.storage.controller.dto;

import com.music.storage.model.StorageType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageCreateDto {

    @NotNull(message = "Storage type is required: STAGING or PERMANENT")
    private StorageType storageType;

    @NotBlank(message = "Bucket is required")
    @Size(max = 255, message = "Bucket must be at most 255 characters")
    private String bucket;

    @NotBlank(message = "Path is required")
    @Size(max = 255, message = "Path must be at most 255 characters")
    private String path;

}
