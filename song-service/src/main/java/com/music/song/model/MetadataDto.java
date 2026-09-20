package com.music.song.model;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetadataDto {

    private Long resourceId;
    private String name;
    private String artist;
    private String album;
    private String duration;
    private String year;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
