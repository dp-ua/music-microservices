package com.music.processor.metadata;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPDM;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.Mp3Parser;
import org.springframework.stereotype.Component;
import org.xml.sax.helpers.DefaultHandler;

import com.music.processor.exception.MetadataExtractionException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class Mp3MetadataExtractor {

    private static final String DURATION_FORMAT = "%02d:%02d";

    private final Mp3Parser parser = new Mp3Parser();

    public Mp3Metadata extract(byte[] fileData) {
        return extract(new ByteArrayInputStream(fileData));
    }

    public Mp3Metadata extract(InputStream fileData) {
        var tikaMetadata = new Metadata();
        try {
            parser.parse(fileData, new DefaultHandler(), tikaMetadata, new ParseContext());
        } catch (Exception e) {
            log.error("Metadata extraction error: ", e);
            throw new MetadataExtractionException(e);
        }

        var metadata = Mp3Metadata.builder()
                .name(tikaMetadata.get(TikaCoreProperties.TITLE))
                .artist(tikaMetadata.get(XMPDM.ARTIST))
                .album(tikaMetadata.get(XMPDM.ALBUM))
                .duration(formatDuration(tikaMetadata.get(XMPDM.DURATION)))
                .year(tikaMetadata.get(XMPDM.RELEASE_DATE))
                .build();

        log.info("Metadata successfully extracted: {}", metadata);
        return metadata;
    }

    private String formatDuration(String seconds) {
        if (isBlank(seconds)) {
            return null;
        }
        var totalSeconds = Math.round(Double.parseDouble(seconds));
        return DURATION_FORMAT.formatted(totalSeconds / 60, totalSeconds % 60);
    }

}
