package com.music.resource.client.song;

import org.springframework.stereotype.Service;

import com.music.resource.client.song.dto.ClientMetadataUploadDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceClientProxyImpl implements SongServiceClientProxy {

    private final SongServiceClient songServiceClient;

    @Override
    public void uploadMetadata(ClientMetadataUploadDto clientDto) {
        log.info("Uploading metadata for resourceId: {}", clientDto.getResourceId());
        try {
            songServiceClient.uploadMetadata(clientDto);
            log.info("Metadata uploaded successfully for resourceId: {}", clientDto.getResourceId());
        } catch (feign.RetryableException e) {
            log.error("song-service not available. Metadata not uploaded. resourceId: {}, reason: {}", clientDto.getResourceId(), e.getMessage());
            // TODO: should be thrown own error
        }
    }

}