package com.music.resource.client.song;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.music.resource.client.song.dto.ClientMetadataUploadDto;
import com.music.resource.exception.SongServerException;

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
        var ids = String.valueOf(clientDto.getId());
        log.info("Uploading metadata for id: {}", ids);
        execute(SongOperation.UPLOAD, ids, () -> songServiceClient.uploadMetadata(clientDto));
        log.info("Metadata uploaded successfully for id: {}", ids);
    }

    @Override
    public void delete(List<Long> existingIds) {
        var ids = StringUtils.join(existingIds, ",");
        log.info("Deleting metadata for ids: {}", ids);
        execute(SongOperation.DELETE, ids, () -> songServiceClient.delete(ids));
        log.info("Metadata deleted successfully for ids: {}", ids);
    }

    private void execute(SongOperation op, String ids, Runnable action) {
        try {
            action.run();
        } catch (RetryableException e) {
            log.error("song-service unavailable. operation={}, ids={}", op, ids, e);
            throw SongServerException.unavailable(op, ids, e);
        } catch (FeignException e) {
            log.error("song-service error. operation={}, ids={}, status={}",
                    op, ids, e.status(), e);
            throw SongServerException.upstreamError(op, ids, e);
        }
    }

}