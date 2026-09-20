package com.music.resource.client.song.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientMetadataUploadDto {

    private Long resourceId;
    private String name;
    private String artist;
    private String album;
    private String duration;
    private String year;

}
