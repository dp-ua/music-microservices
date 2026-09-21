package com.music.resource.utils;

import java.io.File;

import com.mpatric.mp3agic.Mp3File;
import com.music.resource.exception.ResourceException;
import com.music.resource.model.MetadataDto;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class MetadataUtilParser {

    private static final String DURATION_FORMAT = "%02d:%02d";

    public MetadataDto parseMetadata(byte[] fileData) {
        File tempFile = null;
        try {
            tempFile = Mp3FileProcessingUtil.createTempFileFromBytes(
                    fileData,
                    Mp3ProcessingConstants.TEMP_FILE_PREFIX_PARSE
            );

            var mp3File = Mp3FileProcessingUtil.parseMp3File(tempFile);
            var metadata = extractMetadata(mp3File);

            log.info("Metadata successfully parsed: {}", metadata);
            return metadata;

        } catch (Exception e) {
            log.error("Parse metadata error: ", e);
            throw ResourceException.canTReadFileData("unknown", e);
        } finally {
            Mp3FileProcessingUtil.deleteTempFile(tempFile);
        }
    }

    private MetadataDto extractMetadata(Mp3File mp3File) {
        var metadata = MetadataDto.builder()
                .duration(formatDuration(mp3File))
                .build();

        if (mp3File.hasId3v2Tag()) {
            var id3v2 = mp3File.getId3v2Tag();
            metadata.setName(id3v2.getTitle());
            metadata.setArtist(id3v2.getArtist());
            metadata.setAlbum(id3v2.getAlbum());
            metadata.setYear(id3v2.getYear());
        } else if (mp3File.hasId3v1Tag()) {
            var id3v1 = mp3File.getId3v1Tag();
            metadata.setName(id3v1.getTitle());
            metadata.setArtist(id3v1.getArtist());
            metadata.setAlbum(id3v1.getAlbum());
            metadata.setYear(id3v1.getYear());
        }

        return metadata;
    }

    private String formatDuration(Mp3File mp3File) {
        long totalSeconds = mp3File.getLengthInSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(DURATION_FORMAT, minutes, seconds);
    }

}
