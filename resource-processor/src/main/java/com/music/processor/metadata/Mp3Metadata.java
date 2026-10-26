package com.music.processor.metadata;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Mp3Metadata {

    private String name;
    private String artist;
    private String album;
    private String duration;
    private String year;

}
