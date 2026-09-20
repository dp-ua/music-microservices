package com.music.song.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataUploadDto {

    @NotNull(message = "id is required")
    @Positive(message = "id must be a positive number")
    private Long id;

    @NotNull(message = "Song name is required")
    @Size(min = 1, max = 100, message = "Song name must be between 1 and 100 characters")
    private String name;

    @NotNull(message = "Artist name is required")
    @Size(min = 1, max = 100, message = "Artist name must be between 1 and 100 characters")
    private String artist;

    @NotNull(message = "Album name is required")
    @Size(min = 1, max = 100, message = "Album name must be between 1 and 100 characters")
    private String album;

    @NotNull(message = "Duration is required")
    @Pattern(
            regexp = "^\\d{2}:[0-5]\\d$",
            message = "Duration must be in mm:ss format with leading zeros"
    )
    private String duration;

    @NotNull(message = "Year is required")
    @Pattern(
            regexp = "^(19\\d{2}|20\\d{2})$",
            message = "Year must be between 1900 and 2099"
    )
    private String year;

}
