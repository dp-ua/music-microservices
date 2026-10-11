package com.music.resource.client.song;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "song-service")
public interface SongServiceClient {

    @DeleteMapping("${services.config.song-service-client.urls.delete-metadata}")
    void delete(@RequestParam("id") String ids);

}
