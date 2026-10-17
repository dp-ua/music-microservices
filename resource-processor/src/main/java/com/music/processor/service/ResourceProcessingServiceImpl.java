package com.music.processor.service;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.music.processor.client.dto.SongMetadataDto;
import com.music.processor.client.resource.ResourceServiceClientProxy;
import com.music.processor.client.song.SongServiceClientProxy;
import com.music.processor.metadata.Mp3Metadata;
import com.music.processor.metadata.Mp3MetadataExtractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceProcessingServiceImpl implements ResourceProcessingService {

    private final ResourceServiceClientProxy resourceServiceClient;
    private final Mp3MetadataExtractor metadataExtractor;
    private final SongServiceClientProxy songServiceClient;

    @Override
    public void process(Long resourceId) {
        log.info("Processing resource {}", resourceId);

        var fileData = resourceServiceClient.download(resourceId);
        var metadata = metadataExtractor.extract(fileData);
        logMissingFields(resourceId, metadata);

        songServiceClient.saveMetadata(toSongMetadata(resourceId, metadata));
        log.info("Resource {} processed", resourceId);
    }

    private void logMissingFields(Long resourceId, Mp3Metadata metadata) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("name", metadata.getName());
        fields.put("artist", metadata.getArtist());
        fields.put("album", metadata.getAlbum());
        fields.put("duration", metadata.getDuration());
        fields.put("year", metadata.getYear());

        var missing = fields.entrySet().stream()
                .filter(field -> isBlank(field.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        if (!missing.isEmpty()) {
            log.error("Resource {}: the mp3 file has no metadata fields {}, the metadata is sent as it is",
                    resourceId, missing);
        }
    }

    private SongMetadataDto toSongMetadata(Long resourceId, Mp3Metadata metadata) {
        return SongMetadataDto.builder()
                .id(resourceId)
                .name(metadata.getName())
                .artist(metadata.getArtist())
                .album(metadata.getAlbum())
                .duration(metadata.getDuration())
                .year(metadata.getYear())
                .build();
    }

}
