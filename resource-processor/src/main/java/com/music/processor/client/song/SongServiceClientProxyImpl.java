package com.music.processor.client.song;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.music.processor.client.RemoteCallErrors;
import com.music.processor.client.dto.SongMetadataDto;
import com.music.processor.exception.TransientRemoteServiceException;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceClientProxyImpl implements SongServiceClientProxy {

    private static final String SERVICE = "song-service";
    private static final String OPERATION = "save metadata";

    private final SongServiceClient songServiceClient;

    @Override
    @Retryable(
            retryFor = TransientRemoteServiceException.class,
            maxAttemptsExpression = "${processor.retry.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${processor.retry.delay-ms}",
                    multiplierExpression = "${processor.retry.multiplier}"))
    public void saveMetadata(SongMetadataDto metadata) {
        log.info("Saving metadata to {}: {}", SERVICE, metadata);
        try {
            songServiceClient.saveMetadata(metadata);
            log.info("Metadata saved for resource {}", metadata.getId());
        } catch (FeignException.Conflict e) {
            // the message was delivered twice: the metadata is already saved
            log.info("Metadata for resource {} already exists in {}, nothing to do", metadata.getId(), SERVICE);
        } catch (FeignException e) {
            var error = RemoteCallErrors.translate(SERVICE, OPERATION + " " + metadata.getId(), e);
            if (error instanceof TransientRemoteServiceException) {
                log.warn("Attempt failed: {}", error.getMessage());
            }
            throw error;
        }
    }

}
