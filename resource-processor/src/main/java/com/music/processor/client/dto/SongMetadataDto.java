package com.music.processor.client.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Request body of POST /songs in song-service. The id of the song equals the id of the resource.
 */
@Data
@Builder
public class SongMetadataDto {

    private Long id;
    private String name;
    private String artist;
    private String album;
    private String duration;
    private String year;

}
