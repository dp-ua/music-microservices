package com.music.song.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataUploadDto {

    @Positive
    @NotNull
    private Long resourceId;

    @NotNull
    private String name;

    @NotNull
    private String artist;

    @NotNull
    private String album;

    private String duration;

    private String year;

}
