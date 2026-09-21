package com.music.resource.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.mpatric.mp3agic.Mp3File;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class Mp3FileProcessingUtil {

    public File createTempFileFromBytes(byte[] data, String prefix) throws IOException {
        var tempFile = File.createTempFile(prefix, Mp3ProcessingConstants.MP3_EXTENSION);
        Files.write(tempFile.toPath(), data);
        return tempFile;
    }

    public void deleteTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            var deleted = tempFile.delete();
            if (!deleted) {
                log.warn("Can't delete temp file: {}", tempFile.getAbsolutePath());
            }
        }
    }

    public Mp3File parseMp3File(File tempFile) throws Exception {
        return new Mp3File(tempFile);
    }

    public String extractFileNameWithoutExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return Mp3ProcessingConstants.UNKNOWN_FILENAME;
        }
        var lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(0, lastDot) : filename;
    }

}
