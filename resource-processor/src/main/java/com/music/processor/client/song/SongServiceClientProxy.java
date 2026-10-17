package com.music.processor.client.song;

import com.music.processor.client.dto.SongMetadataDto;

public interface SongServiceClientProxy {

    /**
     * Saves the metadata as it is, nothing is filled in by default. Transient failures are retried.
     * 409 (metadata already exists) is treated as success.
     *
     * @throws com.music.processor.exception.RemoteServiceException if the metadata can't be saved
     */
    void saveMetadata(SongMetadataDto metadata);

}
