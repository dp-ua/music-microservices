package com.music.resource.utils;

import java.io.File;

import org.springframework.web.multipart.MultipartFile;

import com.mpatric.mp3agic.Mp3File;
import com.music.resource.exception.ResourceException;
import com.music.resource.model.MetadataDto;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class MetadataUtilParser {

    public MetadataDto parseMetadata(MultipartFile file) {
        File tempFile = null;
        try {
            tempFile = Mp3FileProcessingUtil.createTempFileFromMultipart(
                    file,
                    Mp3ProcessingConstants.TEMP_FILE_PREFIX_PARSE
            );

            var mp3File = Mp3FileProcessingUtil.parseMp3File(tempFile);
            var metadata = extractMetadata(mp3File, file.getOriginalFilename());

            log.info("Metadata successfully parsed: {}", metadata);
            return metadata;

        } catch (Exception e) {
            log.error("Parse metadata error: ", e);
            throw ResourceException.canTReadFileData(file.getOriginalFilename(), e);
        } finally {
            Mp3FileProcessingUtil.deleteTempFile(tempFile);
        }
    }

    private MetadataDto extractMetadata(Mp3File mp3File, String originalFilename) {
        var metadata = MetadataDto.builder()
                .duration(String.valueOf(mp3File.getLengthInSeconds()))
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

        if (metadata.getName() == null || metadata.getName().isEmpty()) {
            var filenameWithoutExtension = Mp3FileProcessingUtil.extractFileNameWithoutExtension(originalFilename);
            metadata.setName(filenameWithoutExtension);
        }

        return metadata;
    }

}
