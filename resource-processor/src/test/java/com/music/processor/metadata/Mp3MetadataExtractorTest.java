package com.music.processor.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.music.processor.exception.MetadataExtractionException;

/**
 * Uses the real MP3 samples from test-data/ (they are added to the test resources by the build).
 */
class Mp3MetadataExtractorTest {

    private static final String VALID_SAMPLE = "/valid-sample-with-required-tags.mp3";
    private static final String SAMPLE_WITH_MISSED_TAGS = "/invalid-sample-with-missed-tags.mp3";

    private final Mp3MetadataExtractor extractor = new Mp3MetadataExtractor();

    @Test
    @DisplayName("All five fields are extracted from an MP3 with the required tags")
    void extractsAllFieldsFromMp3WithRequiredTags() throws IOException {
        var metadata = extractor.extract(readSample(VALID_SAMPLE));

        assertThat(metadata.getName()).isEqualTo("Test Title");
        assertThat(metadata.getArtist()).isEqualTo("Test Artist");
        assertThat(metadata.getAlbum()).isEqualTo("Test Album");
        assertThat(metadata.getDuration()).isEqualTo("00:08");
        assertThat(metadata.getYear()).isEqualTo("2025");
    }

    @Test
    @DisplayName("The duration has the mm:ss format of song-service")
    void durationHasMinutesAndSecondsFormat() throws IOException {
        var metadata = extractor.extract(readSample(VALID_SAMPLE));

        assertThat(metadata.getDuration()).matches("\\d{2}:[0-5]\\d");
    }

    @Test
    @DisplayName("The same result is returned for a byte array and for a stream")
    void byteArrayAndStreamGiveTheSameResult() throws IOException {
        var data = readSample(VALID_SAMPLE);

        var fromBytes = extractor.extract(data);
        try (var stream = Mp3MetadataExtractorTest.class.getResourceAsStream(VALID_SAMPLE)) {
            var fromStream = extractor.extract(stream);
            assertThat(fromStream).isEqualTo(fromBytes);
        }
    }

    @Test
    @DisplayName("An MP3 without the required tags is not an error: missing fields stay empty, nothing is filled in")
    void missingTagsStayEmpty() throws IOException {
        var metadata = extractor.extract(readSample(SAMPLE_WITH_MISSED_TAGS));

        var requiredFields = new String[] {
                metadata.getName(), metadata.getArtist(), metadata.getAlbum(), metadata.getYear() };
        assertThat(requiredFields).anyMatch(value -> value == null || value.isBlank());
    }

    @Test
    @DisplayName("Bytes that are not an MP3 file give a metadata extraction error or no metadata at all")
    void notAnMp3File() {
        var notAnMp3 = "this is plain text, not an mp3 file".getBytes();

        try {
            var metadata = extractor.extract(notAnMp3);
            assertThat(metadata.getName()).isNull();
            assertThat(metadata.getArtist()).isNull();
            assertThat(metadata.getAlbum()).isNull();
            assertThat(metadata.getYear()).isNull();
        } catch (MetadataExtractionException e) {
            assertThat(e).hasMessageContaining("Can't extract metadata");
        }
    }

    @Test
    @DisplayName("A failing stream is wrapped into MetadataExtractionException")
    void brokenStreamIsWrapped() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk error");
            }
        };

        assertThatThrownBy(() -> extractor.extract(broken))
                .isInstanceOf(MetadataExtractionException.class)
                .hasRootCauseMessage("disk error");
    }

    private byte[] readSample(String name) throws IOException {
        try (var stream = Mp3MetadataExtractorTest.class.getResourceAsStream(name)) {
            assertThat(stream).as("test resource %s", name).isNotNull();
            return stream.readAllBytes();
        }
    }

}
