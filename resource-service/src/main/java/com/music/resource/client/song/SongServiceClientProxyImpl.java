package com.music.resource.client.song;

import org.springframework.stereotype.Service;

import com.music.resource.client.song.dto.ClientMetadataUploadDto;
import com.music.resource.exception.MetadataUploadException;

import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceClientProxyImpl implements SongServiceClientProxy {

    private final SongServiceClient songServiceClient;

    @Override
    public void uploadMetadata(ClientMetadataUploadDto clientDto) {
        log.info("Uploading metadata for id: {}", clientDto.getId());
        try {
            songServiceClient.uploadMetadata(clientDto);
            log.info("Metadata uploaded successfully for id: {}", clientDto.getId());
        } catch (RetryableException e) {
            throw MetadataUploadException.unavailable(clientDto.getId(), e);
        } catch (FeignException e) {
            throw MetadataUploadException.upstreamError(clientDto.getId(), e);
        }
    }

}