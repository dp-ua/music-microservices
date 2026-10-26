package com.music.processor.service;

public interface ResourceProcessingService {

    /**
     * Downloads the mp3 from resource-service, extracts its metadata and saves it in song-service.
     *
     * @throws RuntimeException if any step fails
     */
    void process(Long resourceId);

}
