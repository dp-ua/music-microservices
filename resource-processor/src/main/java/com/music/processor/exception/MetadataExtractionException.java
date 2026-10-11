package com.music.processor.exception;

public class MetadataExtractionException extends RuntimeException {

    private static final String CANNOT_EXTRACT = "Can't extract metadata from the mp3 file";

    public MetadataExtractionException(Throwable cause) {
        super(CANNOT_EXTRACT, cause);
    }

}
