package com.music.resource.client.song;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.music.resource.client.song.dto.ClientMetadataUploadDto;

@FeignClient(name = "song-service", url = "${services.config.song-service-client.base-url}")
public interface SongServiceClient {

    @PostMapping("${services.config.song-service-client.urls.post-metadata}")
    void uploadMetadata(@RequestBody ClientMetadataUploadDto metadata);

}
