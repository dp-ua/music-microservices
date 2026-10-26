package com.music.processor.client.song;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.music.processor.client.dto.SongMetadataDto;

/**
 * Found by name in Eureka, every call is balanced between all running instances.
 */
@FeignClient(name = "song-service")
public interface SongServiceClient {

    @PostMapping(path = "/songs", consumes = "application/json")
    void saveMetadata(@RequestBody SongMetadataDto metadata);

}
